package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits the enum for a protobuf enum: the schema's constants plus {@code UNRECOGNIZED} for value numbers a newer
 * writer may send, since proto3 enums are open. The number mapping lives on the enum itself, so nothing else is
 * needed to read or write it.
 */
final class EnumWriter {

    private EnumWriter() {
    }

    static String write(EnumDescriptor enumType) {
        SourceWriter w = new SourceWriter();
        String packageName = Names.javaPackage(enumType.getFile());
        if (!packageName.isEmpty()) w.line("package " + packageName + ";").line("");
        w.line("import javax.annotation.processing.Generated;");
        w.line("");
        body(w, enumType, true);
        return w.toString();
    }

    /** The enum declaration itself, for a file of its own or nested inside a record. */
    static void body(SourceWriter w, EnumDescriptor enumType, boolean topLevel) {
        Map<String, Integer> constants = new LinkedHashMap<>();
        for (EnumValueDescriptor value : enumType.getValues()) {
            constants.putIfAbsent(Names.enumConstant(value), value.getNumber());
        }
        w.line("/** Generated from {@code " + enumType.getFullName() + "}. */");
        if (topLevel) w.line("@Generated(\"" + Generator.NAME + "\")");
        w.open("public enum " + enumType.getName() + " {");
        w.line("");
        for (Map.Entry<String, Integer> constant : constants.entrySet()) {
            w.line(constant.getKey() + "(" + constant.getValue() + "),");
        }
        w.line("/** A value number this build does not know; it can be decoded but not encoded. */");
        w.line("UNRECOGNIZED(-1);");
        w.line("");
        w.line("private final int number;");
        w.line("");
        w.open(enumType.getName() + "(int number) {");
        w.line("this.number = number;");
        w.close();
        w.line("");
        w.line("/** The value number to write. */");
        w.open("public int number() {");
        w.line("if (this == UNRECOGNIZED) throw new IllegalArgumentException(\"" + enumType.getFullName()
            + ".UNRECOGNIZED cannot be encoded\");");
        w.line("return number;");
        w.close();
        w.line("");
        w.line("/** The constant for a value number, or {@link #UNRECOGNIZED} for one this build does not know. */");
        w.open("public static " + enumType.getName() + " forNumber(int number) {");
        w.open("return switch (number) {");
        for (Map.Entry<String, Integer> constant : constants.entrySet()) {
            w.line("case " + constant.getValue() + " -> " + constant.getKey() + ";");
        }
        w.line("default -> UNRECOGNIZED;");
        w.close("};");
        w.close();
        w.close();
    }
}
