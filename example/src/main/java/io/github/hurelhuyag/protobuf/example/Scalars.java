package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

import java.nio.ByteBuffer;

/**
 * Binds to {@code example.Scalars}: implicit presence, so primitives. Java has no unsigned types; uint32/uint64
 * arrive as their two's-complement bit patterns. A {@code null} String or ByteBuffer encodes like the empty default.
 * <p>
 * {@code bytes} decodes to a read-only {@link ByteBuffer} and encodes the buffer's remaining bytes; buffers compare
 * by content, so record {@code equals} works. {@code byte[]} is accepted too, but arrays compare by reference.
 */
@ProtoMessage
public record Scalars(
    @Proto(1) double doubleValue,
    @Proto(2) float floatValue,
    @Proto(3) int int32Value,
    @Proto(4) long int64Value,
    @Proto(5) int uint32Value,
    @Proto(6) long uint64Value,
    @Proto(7) int sint32Value,
    @Proto(8) long sint64Value,
    @Proto(9) int fixed32Value,
    @Proto(10) long fixed64Value,
    @Proto(11) int sfixed32Value,
    @Proto(12) long sfixed64Value,
    @Proto(13) boolean boolValue,
    @Proto(14) String stringValue,
    @Proto(15) ByteBuffer bytesValue
) {
}
