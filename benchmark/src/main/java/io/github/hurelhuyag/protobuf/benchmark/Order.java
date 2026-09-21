package io.github.hurelhuyag.protobuf.benchmark;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ProtoMessage("bench.Order")
public record Order(
    @Proto(1) long id,
    @Proto(2) String customerId,
    @Proto(3) Status status,
    @Proto(4) Instant createdAt,
    @Proto(5) Address shipping,
    @Proto(6) List<Line> lines,
    @Proto(7) Map<String, String> attributes,
    @Proto(8) Integer discountPercent,
    @Proto(9) boolean gift,
    @Proto(10) double weightKg,
    @Proto(11) List<Long> couponIds
) {
}
