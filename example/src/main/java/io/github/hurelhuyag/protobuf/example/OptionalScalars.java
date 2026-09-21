package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

import java.nio.ByteBuffer;

/**
 * Binds to {@code example.OptionalScalars}: explicit presence, so boxed types. {@code null} means the field is
 * absent; a non-null default (0, "", false) is written to the wire. Declaring {@code int} here is a compile error.
 */
@ProtoMessage("example.OptionalScalars")
public record OptionalScalars(
    @Proto(1) Double doubleValue,
    @Proto(2) Float floatValue,
    @Proto(3) Integer int32Value,
    @Proto(4) Long int64Value,
    @Proto(5) Integer uint32Value,
    @Proto(6) Long uint64Value,
    @Proto(7) Integer sint32Value,
    @Proto(8) Long sint64Value,
    @Proto(9) Integer fixed32Value,
    @Proto(10) Long fixed64Value,
    @Proto(11) Integer sfixed32Value,
    @Proto(12) Long sfixed64Value,
    @Proto(13) Boolean boolValue,
    @Proto(14) String stringValue,
    @Proto(15) ByteBuffer bytesValue,
    @Proto(16) Color color
) {
}
