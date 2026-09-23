package io.github.hurelhuyag.protobuf.generator;

/**
 * A value the schema asked to surface as a Java type of its own: the wire-side value is read and written exactly
 * as it would otherwise be, with a {@code ProtoConverter} mapping it on the way in and out.
 */
record ConverterValue(Value wire, String javaType, String converterClass) implements Value {

    /** Name of the static field holding the converter in a generated codec. */
    static String instanceName(String converterClass) {
        return "converter_" + converterClass.replace('.', '_');
    }

    private String instance() {
        return instanceName(converterClass);
    }

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
        return fromWire(wire.defaultValue());
    }

    @Override
    public String isSet(String x) {
        return x + " != null";
    }

    @Override
    public int wireType() {
        return wire.wireType();
    }

    @Override
    public String read() {
        return fromWire(wire.read());
    }

    @Override
    public String write(int fieldNumber, String x) {
        return wire.write(fieldNumber, toWire(x));
    }

    @Override
    public String size(int fieldNumber, String x) {
        return wire.size(fieldNumber, toWire(x));
    }

    @Override
    public boolean packable() {
        return wire.packable();
    }

    @Override
    public String writeNoTag(String x) {
        return wire.writeNoTag(toWire(x));
    }

    @Override
    public String sizeNoTag(String x) {
        return wire.sizeNoTag(toWire(x));
    }

    @Override
    public String fromWire(String value) {
        return instance() + ".fromWire(" + value + ")";
    }

    @Override
    public String toWire(String value) {
        return instance() + ".toWire(" + value + ")";
    }

    @Override
    public String converterClass() {
        return converterClass;
    }
}
