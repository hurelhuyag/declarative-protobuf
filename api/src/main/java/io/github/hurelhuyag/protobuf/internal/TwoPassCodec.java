package io.github.hurelhuyag.protobuf.internal;

import com.google.protobuf.CodedOutputStream;

import java.io.IOException;

/**
 * Implemented by generated codecs: an encode split into a size pass that records every embedded message's size in
 * an {@link EmbeddedSizes} table and an encode pass that reads them back, so nothing is sized twice.
 */
public interface TwoPassCodec<T> extends ProtoCodec<T> {

    int computeSize(T value, EmbeddedSizes sizes);

    void encode(T value, CodedOutputStream out, EmbeddedSizes sizes) throws IOException;
}
