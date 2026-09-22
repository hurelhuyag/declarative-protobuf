package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.WireFormat;

/** An enum field, read and written through the generated enum's own {@code forNumber} / {@code number}. */
record EnumValue(String javaType, String zeroConstant) implements Value {

    @Override
    public String type() {
        return javaType;
    }

    @Override
    public String boxedType() {
        return javaType;
    }

    @Override
    public String defaultValue() {
        return javaType + "." + zeroConstant;
    }

    @Override
    public String isSet(String x) {
        return x + " != null && " + x + ".number() != 0";
    }

    @Override
    public int wireType() {
        return WireFormat.WIRETYPE_VARINT;
    }

    @Override
    public String read() {
        return javaType + ".forNumber(in.readEnum())";
    }

    @Override
    public String write(int fieldNumber, String x) {
        return "out.writeEnum(" + fieldNumber + ", " + x + ".number())";
    }

    @Override
    public String size(int fieldNumber, String x) {
        return "CodedOutputStream.computeEnumSize(" + fieldNumber + ", " + x + ".number())";
    }

    @Override
    public boolean packable() {
        return true;
    }

    @Override
    public String writeNoTag(String x) {
        return "out.writeEnumNoTag(" + x + ".number())";
    }

    @Override
    public String sizeNoTag(String x) {
        return "CodedOutputStream.computeEnumSizeNoTag(" + x + ".number())";
    }
}
