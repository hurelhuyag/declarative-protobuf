package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

/** Wire-side form of {@code example.Uuid}; application code sees {@link java.util.UUID} via {@link UuidConverter}. */
@ProtoMessage
public record Uuid(@Proto(1) long msb, @Proto(2) long lsb) {
}
