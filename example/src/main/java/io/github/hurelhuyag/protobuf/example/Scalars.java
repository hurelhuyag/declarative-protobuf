package io.github.hurelhuyag.protobuf.example;

import com.google.protobuf.ByteString;
import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

/**
 * Binds to {@code example.Scalars}: implicit presence, so primitives. Java has no unsigned types; uint32/uint64
 * arrive as their two's-complement bit patterns. A {@code null} String or ByteString encodes like the empty default.
 * <p>
 * {@code bytes} may also be declared as {@code byte[]}; ByteString is used here because records compare arrays by
 * reference, which would make {@code equals} useless for round-trip checks.
 */
@ProtoMessage("example.Scalars")
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
    @Proto(15) ByteString bytesValue
) {
}
