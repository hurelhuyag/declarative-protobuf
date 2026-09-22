package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.WireFormat;

/**
 * A message field: a generated record through its codec's static helpers (monomorphic call sites), or a
 * well-known type through a built-in codec.
 *
 * @param codec for a generated record the codec class name, for a well-known type the {@code WellKnownCodecs} field
 */
record MessageValue(String javaType, String codec, boolean generated) implements Value {

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
        return "null";
    }

    @Override
    public String isSet(String x) {
        return x + " != null";
    }

    @Override
    public int wireType() {
        return WireFormat.WIRETYPE_LENGTH_DELIMITED;
    }

    @Override
    public String read() {
        return generated ? codec + ".readEmbedded(in)" : "Wire.readMessage(in, " + codec + ")";
    }

    @Override
    public String write(int fieldNumber, String x) {
        return generated
            ? codec + ".writeEmbedded(out, " + fieldNumber + ", " + x + ", sizes)"
            : "Wire.writeMessage(out, " + fieldNumber + ", " + codec + ", " + x + ", sizes)";
    }

    @Override
    public String size(int fieldNumber, String x) {
        return generated
            ? codec + ".embeddedSize(" + fieldNumber + ", " + x + ", sizes)"
            : "Wire.computeMessageSize(" + fieldNumber + ", " + codec + ", " + x + ", sizes)";
    }
}
