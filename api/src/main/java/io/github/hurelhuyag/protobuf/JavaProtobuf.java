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
 * Reads and writes the records the generator produced from your {@code .proto} files. Everything here speaks JDK
 * types only: {@code byte[]}, {@link ByteBuffer}, streams and {@link IOException}, never a protobuf type.
 * <p>
 * The codec for {@code com.acme.Order} is the generated class {@code com.acme.OrderProtoCodec}; for a nested
 * record {@code com.acme.Outer.Inner} it is {@code com.acme.Outer_InnerProtoCodec}. Lookups are cached per class.
 */
public final class JavaProtobuf {

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
                    "No generated codec " + codecName + " for " + type.getName() + "; was this record generated"
                        + " from a .proto file?",
                    e
                );
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalStateException("Generated codec " + codecName + " has an unexpected shape", e);
            }
        }
    };

    private JavaProtobuf() {
    }

    /** Name of the codec class generated for a record type. */
    public static String codecClassName(Class<?> type) {
        return type.getName().replace('$', '_') + "ProtoCodec";
    }

    @SuppressWarnings("unchecked")
    private static <T> TwoPassCodec<T> codec(Class<T> type) {
        return (TwoPassCodec<T>) CODECS.get(type);
    }

    public static <T> T decode(byte[] input, Class<T> type) throws IOException {
        return decode(CodedInputStream.newInstance(input), type);
    }

    /** Decodes the buffer's remaining bytes; the buffer's position is not moved. */
    public static <T> T decode(ByteBuffer input, Class<T> type) throws IOException {
        return decode(CodedInputStream.newInstance(input), type);
    }

    public static <T> T decode(InputStream input, Class<T> type) throws IOException {
        return decode(CodedInputStream.newInstance(input), type);
    }

    private static <T> T decode(CodedInputStream input, Class<T> type) throws IOException {
        T value = codec(type).decode(input);
        input.checkLastTagWas(0);
        return value;
    }

    @SuppressWarnings("unchecked")
    public static byte[] encode(Object value) {
        TwoPassCodec<Object> codec = (TwoPassCodec<Object>) codec(value.getClass());
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
    public static void encode(OutputStream output, Object value) throws IOException {
        TwoPassCodec<Object> codec = (TwoPassCodec<Object>) codec(value.getClass());
        EmbeddedSizes sizes = new EmbeddedSizes();
        codec.computeSize(value, sizes);
        CodedOutputStream out = CodedOutputStream.newInstance(output);
        codec.encode(value, out, sizes.rewind());
        out.flush();
    }
}
