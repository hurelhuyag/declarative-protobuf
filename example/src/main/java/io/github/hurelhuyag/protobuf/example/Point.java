package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

/** With no name given, {@code @ProtoMessage} binds to the message whose simple name matches: {@code example.Point}. */
@ProtoMessage
public record Point(@Proto(1) int x, @Proto(2) int y) {
}
