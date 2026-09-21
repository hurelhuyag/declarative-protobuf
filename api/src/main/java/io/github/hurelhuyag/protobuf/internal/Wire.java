package io.github.hurelhuyag.protobuf.internal;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Helpers shared by generated codecs; not intended to be called by application code. */
public final class Wire {

    /** The proto3 default of a {@code bytes} field declared as {@link ByteBuffer}. */
    public static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0).asReadOnlyBuffer();

    private Wire() {
    }

    /** Reads a {@code bytes} value as a read-only buffer over a private copy. */
    public static ByteBuffer readByteBuffer(CodedInputStream in) throws IOException {
        return ByteBuffer.wrap(in.readByteArray()).asReadOnlyBuffer();
    }

    /** Writes the buffer's remaining bytes (position to limit) as a {@code bytes} field; the buffer is not moved. */
    public static void writeByteBuffer(CodedOutputStream out, int fieldNumber, ByteBuffer value) throws IOException {
        out.writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        out.writeUInt32NoTag(value.remaining());
        out.write(value.duplicate());
    }

    public static int computeByteBufferSize(int fieldNumber, ByteBuffer value) {
        int length = value.remaining();
        return CodedOutputStream.computeTagSize(fieldNumber) + CodedOutputStream.computeUInt32SizeNoTag(length)
            + length;
    }

    /** Reads a length-delimited embedded message whose tag has already been consumed. */
    public static <T> T readMessage(CodedInputStream in, ProtoCodec<T> codec) throws IOException {
        int length = in.readRawVarint32();
        int oldLimit = in.pushLimit(length);
        T value = codec.decode(in);
        in.checkLastTagWas(0);
        in.popLimit(oldLimit);
        return value;
    }

    /** Size pass: sizes an embedded message and records it for {@link #writeMessage}. */
    public static <T> int computeMessageSize(int fieldNumber, ProtoCodec<T> codec, T value, EmbeddedSizes sizes) {
        int slot = sizes.reserve();
        int size = codec instanceof TwoPassCodec<T> twoPass
            ? twoPass.computeSize(value, sizes)
            : codec.computeSize(value);
        sizes.set(slot, size);
        return CodedOutputStream.computeTagSize(fieldNumber) + CodedOutputStream.computeUInt32SizeNoTag(size) + size;
    }

    /** Encode pass: writes an embedded message with the size recorded by {@link #computeMessageSize}. */
    public static <T> void writeMessage(
        CodedOutputStream out, int fieldNumber, ProtoCodec<T> codec, T value, EmbeddedSizes sizes
    ) throws IOException {
        out.writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        out.writeUInt32NoTag(sizes.next());
        if (codec instanceof TwoPassCodec<T> twoPass) twoPass.encode(value, out, sizes); else codec.encode(value, out);
    }
}
