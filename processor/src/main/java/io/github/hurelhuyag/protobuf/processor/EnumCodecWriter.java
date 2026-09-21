package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.Descriptors.EnumDescriptor;

import java.util.Map;

/** Emits the {@code <Enum>ProtoCodec} class holding the number mapping for one bound enum. */
final class EnumCodecWriter {

    static String write(
        String packageName, String codecName, String enumType, EnumDescriptor enumDescriptor, Binder.EnumModel model
    ) {
        SourceWriter w = new SourceWriter();
        if (!packageName.isEmpty()) w.line("package " + packageName + ";").line("");
        w.line("import com.google.protobuf.InvalidProtocolBufferException;");
        w.line("");
        w.line("/** Generated number mapping binding {@code " + enumDescriptor.getFullName() + "} to {@link " + enumType
            + "}. */");
        w.line("@javax.annotation.processing.Generated(\"" + DeclarativeProtobufProcessor.class.getName() + "\")");
        w.open("public final class " + codecName + " {");
        w.line("");
        w.open("private " + codecName + "() {").close();
        w.line("");
        w.open("public static " + enumType + " fromNumber(int number) throws InvalidProtocolBufferException {");
        w.open("return switch (number) {");
        for (Map.Entry<Integer, String> constant : model.constantsByNumber().entrySet()) {
            w.line("case " + constant.getKey() + " -> " + enumType + "." + constant.getValue() + ";");
        }
        if (model.unrecognized() != null) {
            w.line("default -> " + enumType + "." + model.unrecognized() + ";");
        } else {
            w.line("default -> throw new InvalidProtocolBufferException(\"unknown value \" + number + \" for enum "
                + enumDescriptor.getFullName() + "\");");
        }
        w.close("};");
        w.close();
        w.line("");
        w.open("public static int toNumber(" + enumType + " value) {");
        w.open("return switch (value) {");
        for (Map.Entry<Integer, String> constant : model.constantsByNumber().entrySet()) {
            w.line("case " + constant.getValue() + " -> " + constant.getKey() + ";");
        }
        if (model.unrecognized() != null) {
            w.line("case " + model.unrecognized() + " -> throw new IllegalArgumentException(\"" + enumType + "."
                + model.unrecognized() + " cannot be encoded\");");
        }
        w.close("};");
        w.close();
        w.close();
        return w.toString();
    }
}
