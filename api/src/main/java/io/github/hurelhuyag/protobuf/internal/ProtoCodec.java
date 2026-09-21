package io.github.hurelhuyag.protobuf.internal;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

import java.io.IOException;

/**
 * Encodes and decodes one message type; what the annotation processor generates for a {@code @ProtoMessage}
 * record, and what the built-in well-known-type codecs implement. Not an extension point: custom Java types plug
 * in through {@link io.github.hurelhuyag.protobuf.ProtoConverter}.
 */
public interface ProtoCodec<T> {

    /** Reads fields until the stream's current limit (or end) is reached and builds the value. */
    T decode(CodedInputStream in) throws IOException;

    /** Writes all fields of the value; no length prefix. */
    void encode(T value, CodedOutputStream out) throws IOException;

    /** Size in bytes that {@link #encode} will produce for the value. */
    int computeSize(T value);
}
