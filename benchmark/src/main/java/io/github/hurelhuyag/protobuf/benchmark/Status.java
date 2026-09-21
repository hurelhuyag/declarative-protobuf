package io.github.hurelhuyag.protobuf.benchmark;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoEnum;
import io.github.hurelhuyag.protobuf.ProtoUnrecognized;

@ProtoEnum("bench.Status")
public enum Status {
    @Proto(0) UNSPECIFIED,
    @Proto(1) NEW,
    @Proto(2) PAID,
    @Proto(3) SHIPPED,
    @ProtoUnrecognized UNRECOGNIZED
}
