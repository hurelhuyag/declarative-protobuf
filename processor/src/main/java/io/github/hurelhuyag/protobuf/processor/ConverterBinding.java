package io.github.hurelhuyag.protobuf.processor;

/**
 * A scalar or enum value passed through a user {@code ProtoConverter}. {@code instance} names the static field
 * holding the converter in the generated codec.
 */
record ConverterBinding(ValueBinding inner, String converterClass, String instance, String componentType)
    implements ValueBinding {

    static String instanceName(String converterClass) {
        return "codec_" + converterClass.replace('.', '_');
    }

    @Override
    public String boxedType() {
        return componentType;
    }

    @Override
    public String defaultValue() {
        return fromWire(inner.defaultValue());
    }

    @Override
    public String isSet(String x) {
        return x + " != null";
    }

    @Override
    public int wireType() {
        return inner.wireType();
    }

    @Override
    public String read() {
        return fromWire(inner.read());
    }

    @Override
    public String write(int fieldNumber, String x) {
        return inner.write(fieldNumber, toWire(x));
    }

    @Override
    public String size(int fieldNumber, String x) {
        return inner.size(fieldNumber, toWire(x));
    }

    @Override
    public boolean packable() {
        return inner.packable();
    }

    @Override
    public String writeNoTag(String x) {
        return inner.writeNoTag(toWire(x));
    }

    @Override
    public String sizeNoTag(String x) {
        return inner.sizeNoTag(toWire(x));
    }

    @Override
    public ValueBinding wire() {
        return inner;
    }

    @Override
    public String fromWire(String wire) {
        return instance + ".fromWire(" + wire + ")";
    }

    @Override
    public String toWire(String x) {
        return instance + ".toWire(" + x + ")";
    }

    @Override
    public String customCodecClass() {
        return converterClass;
    }
}
