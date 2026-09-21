package io.github.hurelhuyag.protobuf;

/**
 * Maps a field's wire-side Java type to a Java type of your choosing. Register implementations in
 * {@code META-INF/services/io.github.hurelhuyag.protobuf.ProtoConverter}; every component whose Java type is
 * {@code T} then goes through the converter whose {@code W} fits the field — singular components, list elements,
 * map keys and values alike — with no annotation on the field.
 * <p>
 * {@code W} is the type the component would otherwise be declared as: the boxed scalar type ({@code Integer},
 * {@code Long}, {@code String}, {@code ByteBuffer}, ...) for a scalar or enum field, or a {@code @ProtoMessage}
 * record (or the built-in Java type of a well-known message, e.g. {@code Instant}) for a message field; the
 * generated codec still does the wire work, the converter only maps values.
 * <p>
 * Implementations need a no-arg constructor accessible from the record's package and must be stateless: one
 * instance is shared per generated codec. For a scalar field with implicit presence, {@link #fromWire} receives
 * the wire default ({@code 0}, {@code ""}, an empty buffer) when the field is absent, and a value whose
 * {@link #toWire} result is the wire default is not written. Absent message fields stay {@code null} without the
 * converter being called.
 *
 * @param <W> wire-side type
 * @param <T> component type
 */
public interface ProtoConverter<W, T> {

    T fromWire(W wire);

    W toWire(T value);
}
