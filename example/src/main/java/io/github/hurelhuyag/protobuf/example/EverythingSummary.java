package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoMessage;

import java.time.Instant;

/**
 * A second record bound to the same message, declaring only the fields it cares about. Decoding skips the rest;
 * encoding writes only these. Useful for read models and for consumers that own a small slice of a big schema.
 */
@ProtoMessage("example.Everything")
public record EverythingSummary(@Proto(7) Color color, @Proto(24) String label, @Proto(27) Instant createdAt) {
}
