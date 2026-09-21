package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

import java.util.List;

/** Recursive messages just work; an absent repeated field decodes to {@code List.of()}, never null. */
@ProtoMessage
public record Tree(@Proto(1) String name, @Proto(2) List<Tree> children) {
}
