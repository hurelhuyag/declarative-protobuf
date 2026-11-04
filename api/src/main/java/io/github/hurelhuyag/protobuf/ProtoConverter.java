package io.github.hurelhuyag.protobuf;

/**
 * Maps a generated wire-side type to a Java type of your own. The build names the converter, not the schema, which
 * other languages share: the generator's {@code --convert=<message or field>=<java type>:<converter>} on a message
 * converts every field of that message type, on a single field just that one.
 * <pre>
 * message Uuid {           // --convert=example.Uuid=java.util.UUID:com.acme.UuidConverter
 *   fixed64 msb = 1;
 *   fixed64 lsb = 2;
 * }
 *
 * public final class UuidConverter implements ProtoConverter&lt;ProtoUuid, UUID&gt; { ... }
 * </pre>
 * The record of a converted message is generated with a {@code Proto} prefix ({@code ProtoUuid}): it is only the
 * wire side of {@code T}, and the prefix keeps it from being mistaken for it.
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
