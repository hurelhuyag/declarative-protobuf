package io.github.hurelhuyag.protobuf.processor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fixture sources compiled by the tests. */
final class Fixtures {

    static final String STATUS = """
        package fixture;

        import io.github.hurelhuyag.protobuf.*;

        @ProtoEnum("test.Status")
        public enum Status {
            @Proto(0) UNSPECIFIED,
            @Proto(1) OPEN,
            @Proto(2) CLOSED,
            @ProtoUnrecognized UNRECOGNIZED
        }
        """;

    static final String LINE = """
        package fixture;

        import io.github.hurelhuyag.protobuf.*;

        @ProtoMessage
        public record Line(@Proto(1) String sku, @Proto(2) int qty, @Proto(3) double price) {
        }
        """;

    static final String ORDER = """
        package fixture;

        import com.google.protobuf.ByteString;
        import io.github.hurelhuyag.protobuf.*;
        import java.time.Duration;
        import java.time.Instant;
        import java.util.List;
        import java.util.Map;

        @ProtoMessage("test.Order")
        public record Order(
            @Proto(1) long id,
            @Proto(2) String customer,
            @Proto(3) List<Line> lines,
            @Proto(4) List<Integer> tags,
            @Proto(5) Map<String, Integer> attributes,
            @Proto(6) Status status,
            @Proto(7) Integer discount,
            @Proto(8) Instant createdAt,
            @Proto(9) String card,
            @Proto(10) Long cashCents,
            @Proto(11) ByteString blob,
            @Proto(12) Line primary,
            @Proto(13) List<Status> history,
            @Proto(14) int delta,
            @Proto(15) long hash,
            @Proto(16) boolean flag,
            @Proto(17) int unsigned,
            @Proto(18) float ratio,
            @Proto(19) Map<Long, Line> linesById,
            @Proto(20) Duration ttl,
            @Proto(21) Integer maybe,
            @Proto(22) List<String> notes,
            @Proto(23) int statusNum,
            @Proto(24) Tree tree
        ) {
        }
        """;

    static final String TREE = """
        package fixture;

        import io.github.hurelhuyag.protobuf.*;
        import java.util.List;

        @ProtoMessage
        public record Tree(@Proto(1) int value, @Proto(2) List<Tree> children) {
        }
        """;

    static final String OUTER = """
        package fixture;

        import io.github.hurelhuyag.protobuf.*;

        @ProtoMessage
        public record Outer(@Proto(1) Inner inner) {

            @ProtoMessage("test.Outer.Inner")
            public record Inner(@Proto(1) int n) {
            }
        }
        """;

    /** A partial projection of Order, binding bytes to byte[]. */
    static final String BLOB_ONLY = """
        package fixture;

        import io.github.hurelhuyag.protobuf.*;

        @ProtoMessage("test.Order")
        public record BlobOnly(@Proto(11) byte[] blob, @Proto(2) String customer) {
        }
        """;

    static final String SAMPLES = """
        package fixture;

        import com.google.protobuf.ByteString;
        import java.time.Duration;
        import java.time.Instant;
        import java.util.List;
        import java.util.Map;

        public final class Samples {

            public static Order order() {
                return new Order(
                    42L, "ann",
                    List.of(new Line("a", 1, 1.5), new Line("b", 2, 0)),
                    List.of(1, 2, 300),
                    Map.of("k", 7),
                    Status.OPEN,
                    0,
                    Instant.ofEpochSecond(1_700_000_000L, 123),
                    "visa", null,
                    ByteString.copyFromUtf8("xyz"),
                    new Line("p", 3, 2.0),
                    List.of(Status.OPEN, Status.CLOSED),
                    -5, 0xDEADBEEFL, true, -1, 1.5f,
                    Map.of(9L, new Line("q", 1, 0)),
                    Duration.ofMillis(-1500),
                    0,
                    List.of("n1", "n2"),
                    2,
                    new Tree(1, List.of(new Tree(2, List.of()), new Tree(3, List.of(new Tree(4, List.of())))))
                );
            }

            public static Order empty() {
                return new Order(
                    0L, "", List.of(), List.of(), Map.of(), Status.UNSPECIFIED, null, null, null, null,
                    ByteString.EMPTY, null, List.of(), 0, 0L, false, 0, 0f, Map.of(), null, null, List.of(), 0, null
                );
            }

            public static Order bothPayments() {
                return new Order(
                    0L, "", List.of(), List.of(), Map.of(), Status.UNSPECIFIED, null, null, "visa", 5L,
                    ByteString.EMPTY, null, List.of(), 0, 0L, false, 0, 0f, Map.of(), null, null, List.of(), 0, null
                );
            }

            public static Order unrecognizedStatus() {
                return new Order(
                    0L, "", List.of(), List.of(), Map.of(), Status.UNRECOGNIZED, null, null, null, null,
                    ByteString.EMPTY, null, List.of(), 0, 0L, false, 0, 0f, Map.of(), null, null, List.of(), 0, null
                );
            }
        }
        """;


    /** int32 <-> Percent; converters may be non-public when they live in the record's package. */
    static final String PERCENT_CODEC = """
        package fixture;

        import io.github.hurelhuyag.protobuf.ProtoConverter;

        final class PercentCodec implements ProtoConverter<Integer, Percent> {
            @Override public Percent fromWire(Integer wire) { return new Percent(wire); }
            @Override public Integer toWire(Percent value) { return value.basisPoints(); }
        }
        """;

    static final String PERCENT = """
        package fixture;

        public record Percent(int basisPoints) {
        }
        """;

    /** A converter for a message field: the generated Line codec does the wire work, this maps Line <-> String. */
    static final String LINE_TEXT_CODEC = """
        package fixture;

        import io.github.hurelhuyag.protobuf.ProtoConverter;

        public final class LineTextCodec implements ProtoConverter<Line, String> {
            @Override public String fromWire(Line wire) { return wire.sku() + "x" + wire.qty(); }
            @Override public Line toWire(String value) {
                String[] parts = value.split("x");
                return new Line(parts[0], Integer.parseInt(parts[1]), 0);
            }
        }
        """;

    /**
     * Partial Order whose components resolve to registered converters by type: UUID to ServiceUuidCodec
     * (processor path), Percent to PercentCodec and String-on-a-message to LineTextCodec (services file on the
     * classpath).
     */
    static final String CUSTOM = """
        package fixture;

        import io.github.hurelhuyag.protobuf.*;
        import java.util.List;
        import java.util.Map;
        import java.util.UUID;

        @ProtoMessage("test.Order")
        public record Custom(
            @Proto(2) UUID customer,
            @Proto(7) Percent discount,
            @Proto(22) List<UUID> notes,
            @Proto(5) Map<String, Percent> attributes,
            @Proto(12) String primary,
            @Proto(4) List<Percent> tags
        ) {
        }
        """;

    /** META-INF/services entries the fixture compilation puts on its compile classpath. */
    static final Map<String, String> SERVICES = Map.of(
        "io.github.hurelhuyag.protobuf.ProtoConverter", "fixture.PercentCodec\nfixture.LineTextCodec\n"
    );




    /** int64 cents <-> BigDecimal; registered through a META-INF/services file on the compile classpath in tests. */
    static final String CENTS_CODEC = """
        package fixture;

        import io.github.hurelhuyag.protobuf.ProtoConverter;
        import java.math.BigDecimal;

        public final class CentsCodec implements ProtoConverter<Long, BigDecimal> {
            @Override public BigDecimal fromWire(Long wire) { return BigDecimal.valueOf(wire, 2); }
            @Override public Long toWire(BigDecimal value) { return value.movePointRight(2).longValueExact(); }
        }
        """;


    static Map<String, String> all() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture.Status", STATUS);
        sources.put("fixture.Line", LINE);
        sources.put("fixture.Order", ORDER);
        sources.put("fixture.Tree", TREE);
        sources.put("fixture.Outer", OUTER);
        sources.put("fixture.BlobOnly", BLOB_ONLY);
        sources.put("fixture.Samples", SAMPLES);
        sources.put("fixture.PercentCodec", PERCENT_CODEC);
        sources.put("fixture.Percent", PERCENT);
        sources.put("fixture.LineTextCodec", LINE_TEXT_CODEC);
        sources.put("fixture.Custom", CUSTOM);
        return sources;
    }
}
