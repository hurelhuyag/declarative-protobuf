package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

/** A bare {@code @ProtoMessage} binds by Java name: {@code java_package} + {@code Point} is this record. */
@ProtoMessage
public record Point(@Proto(1) int x, @Proto(2) int y) {
}
