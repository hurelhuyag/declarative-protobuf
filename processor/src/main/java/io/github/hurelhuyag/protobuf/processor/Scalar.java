package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.WireFormat;

import javax.lang.model.type.TypeKind;

/** Scalar protobuf types and their {@code CodedInputStream}/{@code CodedOutputStream} method families. */
enum Scalar implements ValueBinding {
    INT32("Int32", TypeKind.INT, WireFormat.WIRETYPE_VARINT),
    SINT32("SInt32", TypeKind.INT, WireFormat.WIRETYPE_VARINT),
    UINT32("UInt32", TypeKind.INT, WireFormat.WIRETYPE_VARINT),
    FIXED32("Fixed32", TypeKind.INT, WireFormat.WIRETYPE_FIXED32),
    SFIXED32("SFixed32", TypeKind.INT, WireFormat.WIRETYPE_FIXED32),
    INT64("Int64", TypeKind.LONG, WireFormat.WIRETYPE_VARINT),
    SINT64("SInt64", TypeKind.LONG, WireFormat.WIRETYPE_VARINT),
    UINT64("UInt64", TypeKind.LONG, WireFormat.WIRETYPE_VARINT),
    FIXED64("Fixed64", TypeKind.LONG, WireFormat.WIRETYPE_FIXED64),
    SFIXED64("SFixed64", TypeKind.LONG, WireFormat.WIRETYPE_FIXED64),
    FLOAT("Float", TypeKind.FLOAT, WireFormat.WIRETYPE_FIXED32),
    DOUBLE("Double", TypeKind.DOUBLE, WireFormat.WIRETYPE_FIXED64),
    BOOL("Bool", TypeKind.BOOLEAN, WireFormat.WIRETYPE_VARINT),
    STRING("String", null, WireFormat.WIRETYPE_LENGTH_DELIMITED),
    BYTE_BUFFER("ByteBuffer", null, WireFormat.WIRETYPE_LENGTH_DELIMITED),
    BYTE_ARRAY("ByteArray", null, WireFormat.WIRETYPE_LENGTH_DELIMITED),
    /** An enum field bound to a plain {@code int}: the raw value number. */
    ENUM_NUMBER("Enum", TypeKind.INT, WireFormat.WIRETYPE_VARINT);

    private final String method;
    /** Primitive kind, or null for reference types. */
    final TypeKind primitive;
    private final int wireType;

    Scalar(String method, TypeKind primitive, int wireType) {
        this.method = method;
        this.primitive = primitive;
        this.wireType = wireType;
    }

    static Scalar of(FieldDescriptor.Type type) {
        return switch (type) {
            case INT32 -> INT32;
            case SINT32 -> SINT32;
            case UINT32 -> UINT32;
            case FIXED32 -> FIXED32;
            case SFIXED32 -> SFIXED32;
            case INT64 -> INT64;
            case SINT64 -> SINT64;
            case UINT64 -> UINT64;
            case FIXED64 -> FIXED64;
            case SFIXED64 -> SFIXED64;
            case FLOAT -> FLOAT;
            case DOUBLE -> DOUBLE;
            case BOOL -> BOOL;
            case STRING -> STRING;
            case BYTES -> BYTE_BUFFER;
            case ENUM -> ENUM_NUMBER;
            case MESSAGE, GROUP -> throw new IllegalArgumentException("not a scalar: " + type);
        };
    }

    @Override
    public String boxedType() {
        return switch (this) {
            case STRING -> "java.lang.String";
            case BYTE_BUFFER -> "java.nio.ByteBuffer";
            case BYTE_ARRAY -> "byte[]";
            default -> switch (primitive) {
                case INT -> "java.lang.Integer";
                case LONG -> "java.lang.Long";
                case FLOAT -> "java.lang.Float";
                case DOUBLE -> "java.lang.Double";
                case BOOLEAN -> "java.lang.Boolean";
                default -> throw new IllegalStateException();
            };
        };
    }

    @Override
    public String primitiveType() {
        return primitive == null ? boxedType() : primitive.name().toLowerCase();
    }

    @Override
    public String defaultValue() {
        return switch (this) {
            case STRING -> "\"\"";
            case BYTE_BUFFER -> "Wire.EMPTY_BUFFER";
            case BYTE_ARRAY -> "new byte[0]";
            case BOOL -> "false";
            case FLOAT -> "0F";
            case DOUBLE -> "0D";
            default -> primitive == TypeKind.LONG ? "0L" : "0";
        };
    }

    @Override
    public String isSet(String x) {
        return switch (this) {
            case STRING -> x + " != null && !" + x + ".isEmpty()";
            case BYTE_BUFFER -> x + " != null && " + x + ".hasRemaining()";
            case BYTE_ARRAY -> x + " != null && " + x + ".length != 0";
            case BOOL -> x;
            case FLOAT -> x + " != 0F";
            case DOUBLE -> x + " != 0D";
            default -> x + " != " + (primitive == TypeKind.LONG ? "0L" : "0");
        };
    }

    @Override
    public int wireType() {
        return wireType;
    }

    @Override
    public String read() {
        return switch (this) {
            case STRING -> "in.readStringRequireUtf8()";
            case BYTE_BUFFER -> "Wire.readByteBuffer(in)";
            default -> "in.read" + method + "()";
        };
    }

    @Override
    public String write(int fieldNumber, String x) {
        if (this == BYTE_BUFFER) return "Wire.writeByteBuffer(out, " + fieldNumber + ", " + x + ")";
        return "out.write" + method + "(" + fieldNumber + ", " + x + ")";
    }

    @Override
    public String size(int fieldNumber, String x) {
        if (this == BYTE_BUFFER) return "Wire.computeByteBufferSize(" + fieldNumber + ", " + x + ")";
        return "CodedOutputStream.compute" + method + "Size(" + fieldNumber + ", " + x + ")";
    }

    @Override
    public boolean packable() {
        return primitive != null;
    }

    @Override
    public String writeNoTag(String x) {
        return "out.write" + method + "NoTag(" + x + ")";
    }

    @Override
    public String sizeNoTag(String x) {
        return "CodedOutputStream.compute" + method + "SizeNoTag(" + x + ")";
    }
}
