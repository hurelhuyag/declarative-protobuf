package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.WireFormat;

/**
 * A message field bound to a record (via its generated codec) or to a well-known Java type (via a built-in codec).
 * Record codecs are called through their generated static {@code readEmbedded}/{@code writeEmbedded}/
 * {@code embeddedSize} helpers so every call site stays monomorphic; the built-in codecs go through
 * {@code io.github.hurelhuyag.protobuf.internal.Wire}.
 *
 * @param codec for a record, the codec class name; for a well-known type, the {@code WellKnownCodecs} field
 */
record MessageBinding(String javaType, String codec, boolean generated) implements ValueBinding {

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
