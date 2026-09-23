package io.github.hurelhuyag.protobuf;

/**
 * Maps a generated wire-side type to a Java type of your own. The schema names the converter, with
 * {@code option (declarative.java_converter)} on a message (every field of that message type is converted) or on
 * a single field; {@code (declarative.java_type)} names {@code T}.
 * <pre>
 * message Uuid {
 *   option (declarative.java_type) = "java.util.UUID";
 *   option (declarative.java_converter) = "com.acme.UuidConverter";
 *   fixed64 msb = 1;
 *   fixed64 lsb = 2;
 * }
 *
 * public final class UuidConverter implements ProtoConverter&lt;Uuid, UUID&gt; { ... }
 * </pre>
 * {@code W} is what the generator would otherwise have used: the generated record for a message field, or the
 * boxed scalar type ({@code Integer}, {@code Long}, {@code String}, {@code ByteBuffer}, ...) for a scalar field.
 * The generated codec still does all the wire work; a converter only maps values.
 * <p>
 * Implementations need a public no-arg constructor and must be stateless: one instance is shared per generated
 * codec. For a scalar field with implicit presence, {@link #fromWire} receives the wire default ({@code 0},
 * {@code ""}, an empty buffer) when the field is absent, and a value whose {@link #toWire} result is that default
 * is not written. Absent message fields stay {@code null} without the converter being called.
 *
 * @param <W> wire-side type
 * @param <T> the type your code sees
 */
public interface ProtoConverter<W, T> {

    T fromWire(W wire);

    W toWire(T value);
}
