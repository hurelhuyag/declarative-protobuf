package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;

import java.util.List;

/**
 * Emits the record for one top-level message, with its nested messages, enums and oneof interfaces nested inside
 * it exactly as the schema nests them.
 */
final class RecordWriter {

    private final SourceWriter w = new SourceWriter();
    private final Customizations customizations;

    private RecordWriter(Customizations customizations) {
        this.customizations = customizations;
    }

    static String write(Descriptor message, Customizations customizations) {
        RecordWriter writer = new RecordWriter(customizations);
        String packageName = Names.javaPackage(message.getFile());
        if (!packageName.isEmpty()) writer.w.line("package " + packageName + ";").line("");
        writer.w.line("import javax.annotation.processing.Generated;");
        writer.w.line("");
        writer.record(message, true);
        return writer.w.toString();
    }

    /** Inside the record, its own nested types are in scope, so they need no qualifier at all. */
    private String type(Field field, Descriptor message) {
        return field.type().replace(Names.javaName(message, customizations) + ".", "");
    }

    private void record(Descriptor message, boolean topLevel) {
        List<Field> fields = TypeMapper.fields(message, customizations);
        w.line("/** Generated from {@code " + message.getFullName() + "}. */");
        if (topLevel) w.line("@Generated(\"" + Generator.NAME + "\")");
        for (String annotation : customizations.annotationsFor(message)) w.line("@" + annotation);
        String declaration = "public record " + Names.recordName(message, customizations) + "(";
        List<String> interfaces = customizations.interfacesFor(message);
        String body = (interfaces.isEmpty() ? "" : " implements " + String.join(", ", interfaces)) + " {";
        if (fields.isEmpty()) {
            w.open(declaration + ")" + body);
        } else {
            w.open(declaration);
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                String comma = i < fields.size() - 1 ? "," : "";
                // the schema name only earns a comment where it differs from the component name
                String comment = field.name().equals(field.protoName()) ? "" : " // " + field.protoName();
                w.line(type(field, message) + " " + field.name() + comma + comment);
            }
            w.close(")" + body);
            w.indent();
        }
        compactConstructor(message, fields);
        for (Field field : fields) {
            if (field instanceof Field.Oneof oneof) oneof(oneof);
        }
        for (EnumDescriptor nested : message.getEnumTypes()) {
            w.line("");
            EnumWriter.body(w, nested, false);
        }
        for (Descriptor nested : message.getNestedTypes()) {
            if (nested.getOptions().getMapEntry()) continue;
            w.line("");
            record(nested, false);
        }
        w.close();
    }

    /**
     * Rejects null for every singular field the schema did not mark {@code optional}, so a record that would be an
     * invalid message cannot be built, and normalises absent collections to empty ones, so a component of a list
     * or map type is never null. A primitive cannot be null; a field with presence but no {@code optional} keyword
     * is a message field, and is required like any other.
     */
    private void compactConstructor(Descriptor message, List<Field> fields) {
        List<Field.Singular> required = fields.stream()
            .filter(Field.Singular.class::isInstance)
            .map(Field.Singular.class::cast)
            .filter(field -> !field.descriptor().toProto().getProto3Optional())
            .filter(field -> field.presence() || field.value().type().equals(field.value().boxedType()))
            .toList();
        List<Field> collections = fields.stream()
            .filter(field -> field instanceof Field.Repeated || field instanceof Field.MapField)
            .toList();
        if (required.isEmpty() && collections.isEmpty()) return;
        w.line("");
        w.open("public " + Names.recordName(message, customizations) + " {");
        for (Field.Singular field : required) {
            w.line("java.util.Objects.requireNonNull(" + field.name() + ", \"" + field.descriptor().getFullName()
                + " is not optional\");");
        }
        for (Field field : collections) {
            String empty = field instanceof Field.Repeated ? "java.util.List.of()" : "java.util.Map.of()";
            w.line("if (" + field.name() + " == null) " + field.name() + " = " + empty + ";");
        }
        w.close();
    }

    /**
     * A oneof as a sealed interface with one record per member: the type system carries which member is set, so
     * "at most one" cannot be violated. The permits clause is inferred from the nested declarations.
     */
    private void oneof(Field.Oneof oneof) {
        w.line("");
        w.line("/** Generated from {@code oneof " + oneof.protoName() + "}; null when no member is set. */");
        w.open("public sealed interface " + simpleName(oneof.type()) + " {");
        for (int i = 0; i < oneof.cases().size(); i++) {
            Field.Oneof.Case member = oneof.cases().get(i);
            if (i > 0) w.line("");
            w.line("/** Generated from {@code " + member.descriptor().getName() + "}. */");
            w.line("record " + simpleName(member.type()) + "(" + member.value().type() + " value) implements "
                + simpleName(oneof.type()) + " {}");
        }
        w.close();
    }

    private static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }
}
