package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.JavaProtobuf;

import java.io.IOException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything in this file is hand-written; everything it refers to — {@link Order}, {@link Line}, {@link Status},
 * {@link Order.Payment} — was generated from {@code order.proto} at build time.
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        Order order = sample();

        byte[] bytes = JavaProtobuf.encode(order);
        System.out.println(bytes.length + " bytes: " + HexFormat.of().formatHex(bytes, 0, 24) + "...");

        Order back = JavaProtobuf.decode(bytes, Order.class);
        System.out.println("round trip equal: " + back.equals(order));

        // the oneof is a sealed interface, so the compiler knows the cases are exhaustive
        System.out.println("paid by: " + describe(back.payment()));
    }

    static Order sample() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        return new Order(
            new Uuid(id.getMostSignificantBits(), id.getLeastSignificantBits()),
            "ann",
            Status.PAID,
            Instant.parse("2026-09-23T10:15:30Z"),
            new Address("1 Main St", "Ulaanbaatar", "MN"),
            List.of(new Line("SKU-1", 2, 1999L), new Line("SKU-2", 1, 4950L)),
            Map.of("gift", "yes"),
            10,
            new Order.Payment.CardToken("tok_123")
        );
    }

    static String describe(Order.Payment payment) {
        if (payment instanceof Order.Payment.CardToken card) return "card " + card.value();
        if (payment instanceof Order.Payment.InvoiceRef invoice) return "invoice " + invoice.value();
        return "nothing yet";
    }

    private Main() {
    }
}
