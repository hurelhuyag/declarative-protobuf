package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

/** Nested records are fine; {@code Outer.Inner} gets the codec class {@code Outer_InnerProtoCodec}. */
@ProtoMessage
public record Outer(@Proto(1) Inner inner) {

    @ProtoMessage
    public record Inner(@Proto(1) String id) {
    }
}
