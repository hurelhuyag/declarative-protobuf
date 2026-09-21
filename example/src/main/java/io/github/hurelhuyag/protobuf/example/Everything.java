package io.github.hurelhuyag.protobuf.example;

import com.google.protobuf.ByteString;
import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Binds to {@code example.Everything}. Note what is <em>not</em> here: nothing about wire types, packing, map
 * entries or which fields form the oneof — all of that is read from the schema at compile time.
 */
@ProtoMessage("example.Everything")
public record Everything(
    // embedded messages
    @Proto(1) Scalars scalars,
    @Proto(2) OptionalScalars optionals,
    @Proto(3) Point point,
    @Proto(4) Tree tree,
    @Proto(5) Outer outer,
    @Proto(6) Outer.Inner inner,

    // enums
    @Proto(7) Color color,
    @Proto(8) int rawColor,

    // repeated
    @Proto(9) List<Integer> ints,
    @Proto(10) List<Long> fixedLongs,
    @Proto(11) List<Double> doubles,
    @Proto(12) List<Boolean> bools,
    @Proto(13) List<Color> colors,
    @Proto(14) List<String> strings,
    @Proto(15) List<ByteString> blobs,
    @Proto(16) List<Point> points,

    // maps
    @Proto(17) Map<String, Integer> counts,
    @Proto(18) Map<Long, Point> pointsById,
    @Proto(19) Map<String, Color> colorsByName,
    @Proto(20) Map<Boolean, String> byFlag,
    @Proto(21) Map<Integer, ByteString> blobsById,
    @Proto(22) Map<String, Everything> nested,

    // oneof shape: members have presence, so boxed / nullable
    @Proto(23) Point circleCenter,
    @Proto(24) String label,
    @Proto(25) Integer radius,
    @Proto(26) Color tint,

    // well-known types
    @Proto(27) Instant createdAt,
    @Proto(28) Duration ttl,
    @Proto(29) Double maybeDouble,
    @Proto(30) Float maybeFloat,
    @Proto(31) Long maybeLong,
    @Proto(32) Long maybeUnsignedLong,
    @Proto(33) Integer maybeInt,
    @Proto(34) Integer maybeUnsignedInt,
    @Proto(35) Boolean maybeBool,
    @Proto(36) String maybeString,
    @Proto(37) ByteString maybeBytes,
    @Proto(38) List<Instant> timestamps,
    @Proto(39) Map<String, Duration> durations,

    // custom Java types: UuidCodec is registered in META-INF/services, so every UUID on a string field uses it
    @Proto(40) UUID requestId,
    @Proto(41) List<UUID> relatedIds
) {
}
