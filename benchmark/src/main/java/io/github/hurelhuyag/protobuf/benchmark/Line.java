package io.github.hurelhuyag.protobuf.benchmark;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

import java.util.List;

@ProtoMessage("bench.Line")
public record Line(
    @Proto(1) String sku, @Proto(2) int quantity, @Proto(3) long unitPriceCents, @Proto(4) List<String> tags
) {
}
