package io.github.hurelhuyag.protobuf;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.github.hurelhuyag.protobuf.internal.EmbeddedSizes;
import io.github.hurelhuyag.protobuf.internal.ProtoCodec;
import io.github.hurelhuyag.protobuf.internal.TwoPassCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Entry point: looks up the generated codec for a record type and encodes/decodes with it. Malformed input fails
 * with an {@link IOException} (a {@code com.google.protobuf.InvalidProtocolBufferException} underneath).
 * <p>
 * The codec for {@code com.acme.Order} is the generated class {@code com.acme.OrderProtoCodec}; for a nested
 * record {@code com.acme.Outer.Inner} it is {@code com.acme.Outer_InnerProtoCodec}. Lookups are cached per class.
 */
public final class DeclarativeProtobuf {

    private static final ClassValue<ProtoCodec<?>> CODECS = new ClassValue<>() {
        @Override
        protected ProtoCodec<?> computeValue(Class<?> type) {
            String codecName = codecClassName(type);
            try {
                Class<?> codecClass = Class.forName(codecName, true, type.getClassLoader());
                Field instance = codecClass.getField("INSTANCE");
                return (ProtoCodec<?>) instance.get(null);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(
                    "No generated codec " + codecName + " for " + type.getName() + "; is the type annotated with"
                        + " @ProtoMessage and the annotation processor on the processor path?",
                    e
                );
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalStateException("Generated codec " + codecName + " has an unexpected shape", e);
            }
        }
    };

    private DeclarativeProtobuf() {
    }

    /** Name of the codec class generated for a record type. */
    public static String codecClassName(Class<?> type) {
        return type.getName().replace('$', '_') + "ProtoCodec";
    }

    @SuppressWarnings("unchecked")
    private static <T> TwoPassCodec<T> codec(Class<T> type) {
        return (TwoPassCodec<T>) CODECS.get(type);
    }

    public static <T> T decode(byte[] bytes, Class<T> type) throws IOException {
        return decode(CodedInputStream.newInstance(bytes), type);
    }

    /** Decodes the buffer's remaining bytes; the buffer's position is not moved. */
    public static <T> T decode(ByteBuffer bytes, Class<T> type) throws IOException {
        return decode(CodedInputStream.newInstance(bytes), type);
    }

    public static <T> T decode(InputStream in, Class<T> type) throws IOException {
        return decode(CodedInputStream.newInstance(in), type);
    }

    private static <T> T decode(CodedInputStream in, Class<T> type) throws IOException {
        T value = codec(type).decode(in);
        in.checkLastTagWas(0);
        return value;
    }

    @SuppressWarnings("unchecked")
    public static <T> byte[] encode(T value) {
        TwoPassCodec<T> codec = codec((Class<T>) value.getClass());
        EmbeddedSizes sizes = new EmbeddedSizes();
        byte[] bytes = new byte[codec.computeSize(value, sizes)];
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        try {
            codec.encode(value, out, sizes.rewind());
            out.checkNoSpaceLeft();
        } catch (IOException e) {
            throw new IllegalStateException(
                "Serializing to a byte array threw an IOException (should never happen)", e
            );
        }
        return bytes;
    }

    @SuppressWarnings("unchecked")
    public static <T> void encode(T value, OutputStream out) throws IOException {
        TwoPassCodec<T> codec = codec((Class<T>) value.getClass());
        EmbeddedSizes sizes = new EmbeddedSizes();
        codec.computeSize(value, sizes);
        CodedOutputStream coded = CodedOutputStream.newInstance(out);
        codec.encode(value, coded, sizes.rewind());
        coded.flush();
    }
}
