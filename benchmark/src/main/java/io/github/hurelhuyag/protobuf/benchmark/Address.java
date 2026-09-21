package io.github.hurelhuyag.protobuf.benchmark;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

@ProtoMessage("bench.Address")
public record Address(@Proto(1) String street, @Proto(2) String city, @Proto(3) String zip, @Proto(4) String country) {
}
