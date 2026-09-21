package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.WireFormat;

/** An enum field bound to a Java enum through its generated {@code <Enum>ProtoCodec} number mapping. */
record EnumBinding(String enumType, String codecClass, String zeroConstant) implements ValueBinding {

    @Override
    public String boxedType() {
        return enumType;
    }

    @Override
    public String defaultValue() {
        return enumType + "." + zeroConstant;
    }

    @Override
    public String isSet(String x) {
        return x + " != null && " + codecClass + ".toNumber(" + x + ") != 0";
    }

    @Override
    public int wireType() {
        return WireFormat.WIRETYPE_VARINT;
    }

    @Override
    public String read() {
        return codecClass + ".fromNumber(in.readEnum())";
    }

    @Override
    public String write(int fieldNumber, String x) {
        return "out.writeEnum(" + fieldNumber + ", " + codecClass + ".toNumber(" + x + "))";
    }

    @Override
    public String size(int fieldNumber, String x) {
        return "CodedOutputStream.computeEnumSize(" + fieldNumber + ", " + codecClass + ".toNumber(" + x + "))";
    }

    @Override
    public boolean packable() {
        return true;
    }

    @Override
    public String writeNoTag(String x) {
        return "out.writeEnumNoTag(" + codecClass + ".toNumber(" + x + "))";
    }

    @Override
    public String sizeNoTag(String x) {
        return "CodedOutputStream.computeEnumSizeNoTag(" + codecClass + ".toNumber(" + x + "))";
    }
}
