package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.ProtoConverter;

import java.util.UUID;

/**
 * Named by {@code option (declarative.java_converter)} on the {@code Uuid} message in {@code order.proto}, which
 * is why {@link Order#id()} is a {@link UUID} and not a generated {@code Uuid} record. The generated
 * {@code UuidProtoCodec} still does the wire work; this only maps the two halves.
 */
public final class UuidConverter implements ProtoConverter<Uuid, UUID> {

    @Override
    public UUID fromWire(Uuid wire) {
        return new UUID(wire.msb(), wire.lsb());
    }

    @Override
    public Uuid toWire(UUID value) {
        return new Uuid(value.getMostSignificantBits(), value.getLeastSignificantBits());
    }
}
