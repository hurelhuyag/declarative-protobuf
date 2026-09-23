package io.github.hurelhuyag.protobuf.generator;

/**
 * Code fragments for reading, writing and sizing one value: a singular field, a repeated element, a map key or
 * value, or a oneof case payload. Statements are emitted without trailing semicolons; {@code in} and {@code out}
 * name the coded streams, {@code sizes} the embedded-size table.
 */
interface Value {

    /** The Java type to declare, primitive where one exists. */
    String type();

    /** The Java type usable as a generic argument. */
    String boxedType();

    /** Value of a local with implicit presence, i.e. the proto3 default. */
    String defaultValue();

    /** Expression that is true when {@code x} must be written for an implicit-presence field; tolerates null. */
    String isSet(String x);

    int wireType();

    /** Expression reading one value after its tag has been consumed. */
    String read();

    String write(int fieldNumber, String x);

    String size(int fieldNumber, String x);

    /** Whether the value may appear inside a packed repeated field. */
    default boolean packable() {
        return false;
    }

    default String writeNoTag(String x) {
        throw new UnsupportedOperationException("not packable");
    }

    default String sizeNoTag(String x) {
        throw new UnsupportedOperationException("not packable");
    }

    /** The value as it is read and written; for a converted value, the wire-side value underneath it. */
    default Value wire() {
        return this;
    }

    /** Converts a wire-side value to the type the record declares. */
    default String fromWire(String value) {
        return value;
    }

    /** Converts a declared value to its wire-side form. */
    default String toWire(String value) {
        return value;
    }

    /** The user converter class this value calls, or null. */
    default String converterClass() {
        return null;
    }
}
