package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.ProtoConverter;

import java.util.UUID;

/**
 * Surfaces {@code string} fields as {@link UUID}. Registered in {@code META-INF/services}, it applies to every
 * {@code UUID} component bound to a {@code string} field; the wire type parameter ({@code String}) is what the
 * processor matches against the field. An absent implicit-presence field arrives as {@code ""}, which this
 * converter maps to {@code null}; {@code null} components are not written.
 */
public final class UuidCodec implements ProtoConverter<String, UUID> {

    @Override
    public UUID fromWire(String wire) {
        return wire.isEmpty() ? null : UUID.fromString(wire);
    }

    @Override
    public String toWire(UUID value) {
        return value.toString();
    }
}
