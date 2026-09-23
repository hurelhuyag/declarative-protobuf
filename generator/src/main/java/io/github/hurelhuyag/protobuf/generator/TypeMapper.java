package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chooses the Java type for every schema construct. Nothing here is configurable: because both the record and its
 * codec are generated, one type per proto type is enough, and the choice is the lossless one in each case.
 */
final class TypeMapper {

    /** Well-known messages that map to a JDK type instead of a generated record. */
    private static final Map<String, MessageValue> WELL_KNOWN = Map.ofEntries(
        wellKnown("google.protobuf.Timestamp", "java.time.Instant", "TIMESTAMP"),
        wellKnown("google.protobuf.Duration", "java.time.Duration", "DURATION"),
        wellKnown("google.protobuf.DoubleValue", "java.lang.Double", "DOUBLE_VALUE"),
        wellKnown("google.protobuf.FloatValue", "java.lang.Float", "FLOAT_VALUE"),
        wellKnown("google.protobuf.Int64Value", "java.lang.Long", "INT64_VALUE"),
        wellKnown("google.protobuf.UInt64Value", "java.lang.Long", "UINT64_VALUE"),
        wellKnown("google.protobuf.Int32Value", "java.lang.Integer", "INT32_VALUE"),
        wellKnown("google.protobuf.UInt32Value", "java.lang.Integer", "UINT32_VALUE"),
        wellKnown("google.protobuf.BoolValue", "java.lang.Boolean", "BOOL_VALUE"),
        wellKnown("google.protobuf.StringValue", "java.lang.String", "STRING_VALUE"),
        wellKnown("google.protobuf.BytesValue", "java.nio.ByteBuffer", "BYTE_BUFFER_VALUE")
    );

    private static Map.Entry<String, MessageValue> wellKnown(String message, String javaType, String codec) {
        return Map.entry(message, new MessageValue(javaType, "WellKnownCodecs." + codec, false));
    }

    private TypeMapper() {
    }

    /** True for the {@code google.protobuf.*} messages that map to a JDK type, so no record is generated for them. */
    static boolean isWellKnown(Descriptor message) {
        return WELL_KNOWN.containsKey(message.getFullName());
    }

    /**
     * The components of the record generated for a message, in schema declaration order; a oneof appears at the
     * position of its first member and replaces all of them.
     */
    static List<Field> fields(Descriptor message) {
        List<Field> fields = new ArrayList<>();
        List<OneofDescriptor> done = new ArrayList<>();
        for (FieldDescriptor field : message.getFields()) {
            OneofDescriptor oneof = field.getRealContainingOneof();
            if (oneof == null) {
                fields.add(field(field));
            } else if (!done.contains(oneof)) {
                done.add(oneof);
                fields.add(oneof(oneof));
            }
        }
        return fields;
    }

    private static Field field(FieldDescriptor field) {
        String name = Names.fieldName(field);
        if (field.isMapField()) {
            Descriptor entry = field.getMessageType();
            return new Field.MapField(
                field, name, value(entry.findFieldByNumber(1)), value(entry.findFieldByNumber(2))
            );
        }
        if (field.isRepeated()) {
            return new Field.Repeated(field, name, value(field), field.isPacked());
        }
        return new Field.Singular(field, name, value(field), field.hasPresence());
    }

    private static Field oneof(OneofDescriptor oneof) {
        String type = Names.javaName(oneof.getContainingType()) + "." + Names.oneofType(oneof);
        List<Field.Oneof.Case> cases = new ArrayList<>();
        for (FieldDescriptor member : oneof.getFields()) {
            cases.add(new Field.Oneof.Case(member, type + "." + Names.oneofCase(member), value(member)));
        }
        return new Field.Oneof(oneof, Names.oneofName(oneof), type, cases);
    }

    /** The Java type and coded-stream calls for one value, with any converter the schema asked for applied. */
    static Value value(FieldDescriptor field) {
        Value wire = wireValue(field);
        Options.Converted converted = converterFor(field);
        return converted == null
            ? wire
            : new ConverterValue(wire, converted.javaType(), converted.converterClass());
    }

    /**
     * The converter for a field: its own {@code (declarative.java_converter)} if it has one, otherwise the one on
     * the message type it carries.
     */
    private static Options.Converted converterFor(FieldDescriptor field) {
        Options.Converted own = Options.of(field.toProto().getOptions(), "field '" + field.getFullName() + "'");
        if (own != null) return own;
        if (field.getJavaType() != FieldDescriptor.JavaType.MESSAGE) return null;
        Descriptor message = field.getMessageType();
        if (WELL_KNOWN.containsKey(message.getFullName())) return null;
        return Options.of(message.toProto().getOptions(), "message '" + message.getFullName() + "'");
    }

    /** True when a message is only ever seen through a converter, so nothing refers to its record by name. */
    static Options.Converted converterOn(Descriptor message) {
        return Options.of(message.toProto().getOptions(), "message '" + message.getFullName() + "'");
    }

    private static Value wireValue(FieldDescriptor field) {
        return switch (field.getJavaType()) {
            case INT, LONG, FLOAT, DOUBLE, BOOLEAN, STRING, BYTE_STRING -> Scalar.of(field.getType());
            case ENUM -> new EnumValue(Names.javaName(field.getEnumType()), zeroConstant(field.getEnumType()));
            case MESSAGE -> {
                MessageValue wellKnown = WELL_KNOWN.get(field.getMessageType().getFullName());
                if (wellKnown != null) yield wellKnown;
                if (field.getMessageType().getFullName().startsWith("google.protobuf.")) {
                    throw new IllegalArgumentException("field '" + field.getFullName() + "' uses '"
                        + field.getMessageType().getFullName() + "', which has no Java mapping yet");
                }
                yield new MessageValue(
                    Names.javaName(field.getMessageType()), Names.codecName(field.getMessageType()), true
                );
            }
        };
    }

    /** The constant an absent enum field decodes to: proto3 guarantees a value numbered zero. */
    static String zeroConstant(EnumDescriptor enumType) {
        EnumValueDescriptor zero = enumType.findValueByNumber(0);
        if (zero == null) {
            throw new IllegalArgumentException("enum '" + enumType.getFullName() + "' has no value numbered 0");
        }
        return Names.enumConstant(zero);
    }
}
