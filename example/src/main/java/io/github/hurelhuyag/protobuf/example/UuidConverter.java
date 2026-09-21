package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.ProtoConverter;

import java.util.UUID;

/**
 * Surfaces {@code example.Uuid} fields as {@link UUID}. The generated {@code UuidProtoCodec} does the wire work;
 * this only maps between the record and the JDK type, two long reads/writes each way. Registered in
 * {@code META-INF/services}, so every {@code UUID} component bound to a {@code Uuid} field uses it. Absent fields
 * stay {@code null} and {@code null} components are not written.
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
