package io.github.hurelhuyag.protobuf.benchmark;

import io.github.hurelhuyag.protobuf.DeclarativeProtobuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The record codec and protoc-generated classes must agree byte for byte on a schema protoc compiled. */
class WireCompatibilityTest {

    @Test
    void identicalEncoding() {
        for (int lines : new int[]{0, 1, 20}) {
            byte[] expected = Workload.message(lines, lines / 2, lines / 3).toByteArray();
            assertArrayEquals(expected, DeclarativeProtobuf.encode(Workload.record(lines, lines / 2, lines / 3)));
        }
    }

    @Test
    void decodesProtobufJavaOutput() throws Exception {
        byte[] bytes = Workload.message(20, 10, 8).toByteArray();
        assertEquals(Workload.record(20, 10, 8), DeclarativeProtobuf.decode(bytes, Order.class));
    }

    @Test
    void protobufJavaParsesOurOutput() throws Exception {
        byte[] bytes = DeclarativeProtobuf.encode(Workload.record(20, 10, 8));
        var parsed = io.github.hurelhuyag.protobuf.benchmark.proto.Order.parseFrom(bytes);
        assertEquals(Workload.message(20, 10, 8), parsed);
        assertEquals(0, parsed.getUnknownFields().asMap().size());
    }
}
