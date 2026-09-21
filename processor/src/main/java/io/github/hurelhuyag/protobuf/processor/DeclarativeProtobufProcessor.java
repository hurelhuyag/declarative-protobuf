package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import io.github.hurelhuyag.protobuf.ProtoEnum;
import io.github.hurelhuyag.protobuf.ProtoMessage;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generates a {@code ProtoCodec} for every {@code @ProtoMessage} record and a number mapping for every
 * {@code @ProtoEnum} enum, validating each against the schema in the descriptor set(s) named by the
 * {@value #DESCRIPTORS_OPTION} option (one or more {@code FileDescriptorSet} files, separated by the platform path
 * separator, as produced by {@code protoc --descriptor_set_out=... --include_imports}). Custom
 * {@code ProtoConverter} classes are discovered through {@code META-INF/services}.
 */
@SupportedAnnotationTypes({
    "io.github.hurelhuyag.protobuf.ProtoMessage", "io.github.hurelhuyag.protobuf.ProtoEnum",
    "io.github.hurelhuyag.protobuf.Proto", "io.github.hurelhuyag.protobuf.ProtoUnrecognized"
})
@SupportedOptions(DeclarativeProtobufProcessor.DESCRIPTORS_OPTION)
public final class DeclarativeProtobufProcessor extends AbstractProcessor {

    public static final String DESCRIPTORS_OPTION = "declarative.protobuf.descriptors";

    private Schema schema;
    private String schemaError;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        String option = env.getOptions().get(DESCRIPTORS_OPTION);
        if (option == null || option.isBlank()) {
            schemaError = "no descriptor set configured; pass -A" + DESCRIPTORS_OPTION + "=<path to FileDescriptorSet>"
                + " to javac (maven-compiler-plugin <compilerArgs>)";
            return;
        }
        List<Path> paths = new ArrayList<>();
        for (String part : option.split(File.pathSeparator)) {
            if (!part.isBlank()) paths.add(Path.of(part.trim()));
        }
        try {
            schema = Schema.load(paths);
        } catch (Exception e) {
            schemaError = "cannot load descriptor set " + paths + ": " + e.getMessage();
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return true;
        Set<? extends Element> enums = roundEnv.getElementsAnnotatedWith(ProtoEnum.class);
        Set<? extends Element> records = roundEnv.getElementsAnnotatedWith(ProtoMessage.class);
        if (schema == null) {
            for (Element element : enums) processingEnv.getMessager().printMessage(Kind.ERROR, schemaError, element);
            for (Element element : records) processingEnv.getMessager().printMessage(Kind.ERROR, schemaError, element);
            return true;
        }
        Binder binder = new Binder(processingEnv, schema);
        for (Element element : enums) {
            if (element.getKind() != ElementKind.ENUM) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "@ProtoEnum is only supported on enums", element);
                continue;
            }
            TypeElement enumElement = (TypeElement) element;
            EnumDescriptor enumType = binder.enumType(enumElement);
            if (enumType == null) continue;
            Binder.EnumModel model = binder.enumConstants(enumElement, enumType);
            if (model == null) continue;
            String codec = Binder.codecClass(processingEnv.getElementUtils(), enumElement);
            String source = EnumCodecWriter.write(
                packageOf(enumElement), simpleName(codec), enumElement.getQualifiedName().toString(), enumType, model
            );
            writeSource(codec, source, enumElement);
        }
        for (Element element : records) {
            if (element.getKind() != ElementKind.RECORD) {
                processingEnv.getMessager().printMessage(
                    Kind.ERROR, "@ProtoMessage is only supported on records", element
                );
                continue;
            }
            TypeElement record = (TypeElement) element;
            Descriptor message = binder.message(record);
            if (message == null) continue;
            List<FieldBinding> fields = binder.fields(record, message);
            if (fields == null) continue;
            String codec = Binder.codecClass(processingEnv.getElementUtils(), record);
            String source = new MessageCodecWriter(
                packageOf(record), simpleName(codec), record.getQualifiedName().toString(), message, fields
            ).write();
            writeSource(codec, source, record);
        }
        return true;
    }

    private String packageOf(TypeElement type) {
        return processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
    }

    private static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    private void writeSource(String qualifiedName, String source, Element originating) {
        try (Writer writer = processingEnv.getFiler().createSourceFile(qualifiedName, originating).openWriter()) {
            writer.write(source);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Kind.ERROR, "cannot write " + qualifiedName + ": " + e.getMessage(), originating
            );
        }
    }
}
