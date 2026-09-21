package io.github.hurelhuyag.protobuf.processor;

import io.github.hurelhuyag.protobuf.ProtoConverter;

import java.util.UUID;

/**
 * Registered through {@code META-INF/services} in the test resources, i.e. discovered by the processor's own
 * class loader exactly as a codec library on the annotation processor path would be.
 */
public final class ServiceUuidCodec implements ProtoConverter<String, UUID> {

    @Override
    public UUID fromWire(String wire) {
        return wire.isEmpty() ? null : UUID.fromString(wire);
    }

    @Override
    public String toWire(UUID value) {
        return value.toString();
    }
}
