package io.github.hurelhuyag.protobuf.processor;

/**
 * Code fragments for reading, writing and sizing one value (a singular field, a repeated element, a map key or
 * value). Statements are emitted without trailing semicolons; {@code in}/{@code out} name the coded streams.
 */
interface ValueBinding {

    /** Java type usable as a generic type argument (boxed for primitives). */
    String boxedType();

    /** Java type for a local of implicit presence: primitive where one exists, otherwise {@link #boxedType()}. */
    default String primitiveType() {
        return boxedType();
    }

    /** Initial value of an implicit-presence local, i.e. the proto3 default. */
    String defaultValue();

    /** Expression that is true when {@code x} must be written for an implicit-presence field; tolerates null. */
    String isSet(String x);

    int wireType();

    /** Expression reading one value after its tag has been consumed. */
    String read();

    String write(int fieldNumber, String x);

    String size(int fieldNumber, String x);

    /** Whether the value can appear inside a packed repeated field. */
    default boolean packable() {
        return false;
    }

    default String writeNoTag(String x) {
        throw new UnsupportedOperationException("not packable");
    }

    default String sizeNoTag(String x) {
        throw new UnsupportedOperationException("not packable");
    }

    /**
     * For a converted value, the binding of the wire-side value; singular fields are decoded into a local of that
     * type and converted once when the record is built. Otherwise this binding itself.
     */
    default ValueBinding wire() {
        return this;
    }

    /** Converts a wire-side value (of {@link #wire()}'s type) to the component type. */
    default String fromWire(String wire) {
        return wire;
    }

    /** Converts a component value to the wire-side type. */
    default String toWire(String x) {
        return x;
    }

    /** A user-supplied codec class this binding calls through a static instance, or null. */
    default String customCodecClass() {
        return null;
    }
}
