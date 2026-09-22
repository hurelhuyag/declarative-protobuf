package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.WireFormat;

/** Scalar protobuf types, the Java type each one is generated as, and their coded-stream method families. */
enum Scalar implements Value {
    INT32("Int32", "int", "java.lang.Integer", WireFormat.WIRETYPE_VARINT),
    SINT32("SInt32", "int", "java.lang.Integer", WireFormat.WIRETYPE_VARINT),
    UINT32("UInt32", "int", "java.lang.Integer", WireFormat.WIRETYPE_VARINT),
    FIXED32("Fixed32", "int", "java.lang.Integer", WireFormat.WIRETYPE_FIXED32),
    SFIXED32("SFixed32", "int", "java.lang.Integer", WireFormat.WIRETYPE_FIXED32),
    INT64("Int64", "long", "java.lang.Long", WireFormat.WIRETYPE_VARINT),
    SINT64("SInt64", "long", "java.lang.Long", WireFormat.WIRETYPE_VARINT),
    UINT64("UInt64", "long", "java.lang.Long", WireFormat.WIRETYPE_VARINT),
    FIXED64("Fixed64", "long", "java.lang.Long", WireFormat.WIRETYPE_FIXED64),
    SFIXED64("SFixed64", "long", "java.lang.Long", WireFormat.WIRETYPE_FIXED64),
    FLOAT("Float", "float", "java.lang.Float", WireFormat.WIRETYPE_FIXED32),
    DOUBLE("Double", "double", "java.lang.Double", WireFormat.WIRETYPE_FIXED64),
    BOOL("Bool", "boolean", "java.lang.Boolean", WireFormat.WIRETYPE_VARINT),
    STRING("String", "java.lang.String", "java.lang.String", WireFormat.WIRETYPE_LENGTH_DELIMITED),
    BYTES("ByteBuffer", "java.nio.ByteBuffer", "java.nio.ByteBuffer", WireFormat.WIRETYPE_LENGTH_DELIMITED);

    private final String method;
    private final String type;
    private final String boxedType;
    private final int wireType;

    Scalar(String method, String type, String boxedType, int wireType) {
        this.method = method;
        this.type = type;
        this.boxedType = boxedType;
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
            case BYTES -> BYTES;
            case ENUM, MESSAGE, GROUP -> throw new IllegalArgumentException("not a scalar: " + type);
        };
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String boxedType() {
        return boxedType;
    }

    @Override
    public String defaultValue() {
        return switch (this) {
            case STRING -> "\"\"";
            case BYTES -> "Wire.EMPTY_BUFFER";
            case BOOL -> "false";
            case FLOAT -> "0F";
            case DOUBLE -> "0D";
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> "0L";
            default -> "0";
        };
    }

    @Override
    public String isSet(String x) {
        return switch (this) {
            case STRING -> x + " != null && !" + x + ".isEmpty()";
            case BYTES -> x + " != null && " + x + ".hasRemaining()";
            case BOOL -> x;
            case FLOAT -> x + " != 0F";
            case DOUBLE -> x + " != 0D";
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> x + " != 0L";
            default -> x + " != 0";
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
            case BYTES -> "Wire.readByteBuffer(in)";
            default -> "in.read" + method + "()";
        };
    }

    @Override
    public String write(int fieldNumber, String x) {
        if (this == BYTES) return "Wire.writeByteBuffer(out, " + fieldNumber + ", " + x + ")";
        return "out.write" + method + "(" + fieldNumber + ", " + x + ")";
    }

    @Override
    public String size(int fieldNumber, String x) {
        if (this == BYTES) return "Wire.computeByteBufferSize(" + fieldNumber + ", " + x + ")";
        return "CodedOutputStream.compute" + method + "Size(" + fieldNumber + ", " + x + ")";
    }

    @Override
    public boolean packable() {
        return this != STRING && this != BYTES;
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
