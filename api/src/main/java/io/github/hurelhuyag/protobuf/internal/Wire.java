package io.github.hurelhuyag.protobuf.internal;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

import java.io.IOException;

/** Helpers shared by generated codecs; not intended to be called by application code. */
public final class Wire {

    private Wire() {
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
