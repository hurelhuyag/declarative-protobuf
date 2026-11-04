package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;

import java.util.Set;

/** Proto names to Java names: the same rules protoc uses, so generated code looks like hand-written code. */
final class Names {

    /** Names a component cannot have: keywords, plus the {@link Object} methods an accessor would hide. */
    private static final Set<String> RESERVED = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "_",
        "clone", "equals", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait"
    );

    private Names() {
    }

    /** The Java package of everything generated from a file: {@code java_package}, else the proto package. */
    static String javaPackage(FileDescriptor file) {
        return file.getOptions().hasJavaPackage() ? file.getOptions().getJavaPackage() : file.getPackage();
    }

    /**
     * The simple name of the record generated for a message: the schema's, with {@code Proto} in front when the
     * build converts the message. That record is then only the wire side of the application's own type, and the
     * prefix keeps the two apart: {@code ProtoUuid} next to {@code java.util.UUID}, not {@code Uuid}.
     */
    static String recordName(Descriptor message, Customizations customizations) {
        return customizations.conversionFor(message.getFullName()) == null
            ? message.getName()
            : "Proto" + message.getName();
    }

    /** The qualified Java name of the record or enum generated for a message or enum type. */
    static String javaName(GenericDescriptor type, Customizations customizations) {
        String packageName = javaPackage(type.getFile());
        String path = nestedPath(type, customizations);
        return packageName.isEmpty() ? path : packageName + "." + path;
    }

    /** The type's path inside its file, e.g. {@code Outer.Inner}. */
    private static String nestedPath(GenericDescriptor type, Customizations customizations) {
        Descriptor parent = type instanceof Descriptor message ? message.getContainingType()
            : type instanceof EnumDescriptor enumType ? enumType.getContainingType()
            : null;
        String simpleName = type instanceof Descriptor message
            ? recordName(message, customizations)
            : ((EnumDescriptor) type).getName();
        return parent == null ? simpleName : nestedPath(parent, customizations) + "." + simpleName;
    }

    /** The qualified name of the codec class generated for a message; nested types are flattened with underscores. */
    static String codecName(Descriptor message, Customizations customizations) {
        String packageName = javaPackage(message.getFile());
        String path = nestedPath(message, customizations).replace('.', '_') + "ProtoCodec";
        return packageName.isEmpty() ? path : packageName + "." + path;
    }

    /** The record component name for a field: protobuf's {@code json_name}, which is lowerCamelCase. */
    static String fieldName(FieldDescriptor field) {
        return escape(field.getJsonName());
    }

    /** The record component name for a oneof: lowerCamelCase of the oneof name. */
    static String oneofName(OneofDescriptor oneof) {
        return escape(lowerCamel(oneof.getName()));
    }

    /** The nested sealed interface generated for a oneof, e.g. {@code Payment}. */
    static String oneofType(OneofDescriptor oneof) {
        return upperCamel(oneof.getName());
    }

    /** The record generated for one member of a oneof, e.g. {@code CashCents}. */
    static String oneofCase(FieldDescriptor field) {
        return upperCamel(field.getName());
    }

    /** Enum constant names are kept exactly as the schema spells them, as protoc does. */
    static String enumConstant(EnumValueDescriptor value) {
        return escape(value.getName());
    }

    private static String escape(String name) {
        return RESERVED.contains(name) ? name + "_" : name;
    }

    static String lowerCamel(String snake) {
        String camel = upperCamel(snake);
        return Character.toLowerCase(camel.charAt(0)) + camel.substring(1);
    }

    static String upperCamel(String snake) {
        StringBuilder out = new StringBuilder(snake.length());
        boolean upper = true;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') {
                upper = true;
            } else if (upper) {
                out.append(Character.toUpperCase(c));
                upper = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
