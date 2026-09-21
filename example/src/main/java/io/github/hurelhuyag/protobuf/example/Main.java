package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.DeclarativeProtobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Encode a record, decode it back, decode a partial projection of the same bytes. */
public final class Main {

    public static void main(String[] args) throws IOException {
        Everything value = sample();

        // encode: the record's class picks the generated codec
        byte[] bytes = DeclarativeProtobuf.encode(value);
        String head = HexFormat.of().formatHex(bytes, 0, Math.min(bytes.length, 32));
        System.out.println(bytes.length + " bytes: " + head + "...");

        // decode: the target class picks the codec
        Everything back = DeclarativeProtobuf.decode(bytes, Everything.class);
        System.out.println("round trip equal: " + back.equals(value));

        // a partial record reads the same bytes and ignores everything it does not declare
        EverythingSummary summary = DeclarativeProtobuf.decode(bytes, EverythingSummary.class);
        System.out.println("summary: " + summary);
    }

    static Everything sample() {
        Scalars scalars = new Scalars(
            1.5, 2.5f, -3, -4L, -1, -1L, -7, -8L, 9, 10L, -11, -12L, true, "text", bytes("raw")
        );
        OptionalScalars optionals = new OptionalScalars(
            0.0, null, 0, null, 0, null, 0, null, 0, null, 0, null, false, "", ByteBuffer.allocate(0), Color.UNSPECIFIED
        );
        Point point = new Point(3, 4);
        return new Everything(
            scalars, optionals, point,
            new Tree("root", List.of(new Tree("leaf", List.of()))),
            new Outer(new Outer.Inner("in")), new Outer.Inner("direct"),
            Color.GREEN, 42,
            List.of(1, -2, 300), List.of(1L, -1L), List.of(0.5, -0.5), List.of(true, false),
            List.of(Color.RED, Color.BLUE), List.of("a", "b"), List.of(bytes("x")), List.of(point),
            Map.of("k", 1), Map.of(7L, point), Map.of("red", Color.RED), Map.of(true, "yes"),
            Map.of(1, bytes("b")), Map.of(),
            null, "label", null, null,
            Instant.parse("2026-09-21T12:00:00Z"), Duration.ofMinutes(5),
            1.0, 2.0f, 3L, 4L, 5, 6, true, "s", bytes("b"),
            List.of(Instant.EPOCH, Instant.ofEpochSecond(1)), Map.of("d", Duration.ofSeconds(-1, -500_000_000)),
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            List.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        );
    }

    static ByteBuffer bytes(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    private Main() {
    }
}
