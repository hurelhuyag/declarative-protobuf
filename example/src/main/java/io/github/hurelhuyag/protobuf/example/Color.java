package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoEnum;
import io.github.hurelhuyag.protobuf.ProtoUnrecognized;

/**
 * Binds to {@code example.Color}. Constant names are free; only the numbers matter. The {@link ProtoUnrecognized}
 * constant absorbs numbers a newer writer may send; without it, decoding such a value fails.
 */
@ProtoEnum
public enum Color {
    @Proto(0) UNSPECIFIED,
    @Proto(1) RED,
    @Proto(2) GREEN,
    @Proto(3) BLUE,
    @ProtoUnrecognized UNRECOGNIZED
}
