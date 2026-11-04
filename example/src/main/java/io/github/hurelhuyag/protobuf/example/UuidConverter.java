package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.ProtoConverter;

import java.util.UUID;

/**
 * Named by the {@code --convert=example.orders.Uuid=...} argument in {@code pom.xml}, which is why
 * {@link Order#id()} is a {@link UUID}. The message's own record is generated as {@link ProtoUuid}, prefixed
 * because it is only the wire side of {@link UUID}; its generated codec still does the wire work, and this only
 * maps the two halves.
 */
public final class UuidConverter implements ProtoConverter<ProtoUuid, UUID> {

    @Override
    public UUID fromWire(ProtoUuid wire) {
        return new UUID(wire.msb(), wire.lsb());
    }

    @Override
    public ProtoUuid toWire(UUID value) {
        return new ProtoUuid(value.getMostSignificantBits(), value.getLeastSignificantBits());
    }
}
