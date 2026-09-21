package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.DeclarativeProtobuf;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

class ExampleTest {

    @Test
    void roundTrip() throws Exception {
        Everything value = Main.sample();
        byte[] bytes = DeclarativeProtobuf.encode(value);
        assertEquals(value, DeclarativeProtobuf.decode(bytes, Everything.class));
    }

    @Test
    void partialProjection() throws Exception {
        byte[] bytes = DeclarativeProtobuf.encode(Main.sample());
        EverythingSummary summary = DeclarativeProtobuf.decode(bytes, EverythingSummary.class);
        assertEquals(new EverythingSummary(Color.GREEN, "label", Main.sample().createdAt()), summary);
    }
}
