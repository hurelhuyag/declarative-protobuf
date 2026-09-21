package io.github.hurelhuyag.protobuf.benchmark;

import com.google.protobuf.Timestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The same order built both ways: as our records and as protoc-generated messages. */
final class Workload {

    private Workload() {
    }

    static Order record(int lines, int attributes, int coupons) {
        List<Line> lineList = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            List<String> tags = i % 2 == 0 ? List.of("fragile", "gift") : List.of();
            lineList.add(new Line("SKU-" + (1000 + i), 1 + i % 5, 1999L + i * 100L, tags));
        }
        Map<String, String> attrs = new LinkedHashMap<>();
        for (int i = 0; i < attributes; i++) attrs.put("attr" + i, "value-" + i);
        List<Long> couponList = new ArrayList<>();
        for (int i = 0; i < coupons; i++) couponList.add(100_000L + i);
        return new Order(
            123456789L, "cust-42", Status.PAID, Instant.ofEpochSecond(1_758_450_000L, 123_000_000),
            new Address("1 Main St", "Ulaanbaatar", "14200", "MN"), lineList, attrs, 10, true, 2.75, couponList
        );
    }

    static io.github.hurelhuyag.protobuf.benchmark.proto.Order message(int lines, int attributes, int coupons) {
        var order = io.github.hurelhuyag.protobuf.benchmark.proto.Order.newBuilder()
            .setId(123456789L)
            .setCustomerId("cust-42")
            .setStatus(io.github.hurelhuyag.protobuf.benchmark.proto.Status.PAID)
            .setCreatedAt(Timestamp.newBuilder().setSeconds(1_758_450_000L).setNanos(123_000_000))
            .setShipping(io.github.hurelhuyag.protobuf.benchmark.proto.Address.newBuilder()
                .setStreet("1 Main St").setCity("Ulaanbaatar").setZip("14200").setCountry("MN"))
            .setDiscountPercent(10)
            .setGift(true)
            .setWeightKg(2.75);
        for (int i = 0; i < lines; i++) {
            var line = io.github.hurelhuyag.protobuf.benchmark.proto.Line.newBuilder()
                .setSku("SKU-" + (1000 + i)).setQuantity(1 + i % 5).setUnitPriceCents(1999L + i * 100L);
            if (i % 2 == 0) line.addTags("fragile").addTags("gift");
            order.addLines(line);
        }
        for (int i = 0; i < attributes; i++) order.putAttributes("attr" + i, "value-" + i);
        for (int i = 0; i < coupons; i++) order.addCouponIds(100_000L + i);
        return order.build();
    }
}
