package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.JavaProtobuf;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleTest {

    @Test
    void roundTrip() throws IOException {
        Order order = Main.sample();
        assertEquals(order, JavaProtobuf.decode(JavaProtobuf.encode(order), Order.class));
    }

    @Test
    void streams() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JavaProtobuf.encode(out, Main.sample());
        assertEquals(Main.sample(), JavaProtobuf.decode(new ByteArrayInputStream(out.toByteArray()), Order.class));
    }

    /** Absent fields come back as null (presence) or empty (collections), never as a surprise. */
    @Test
    void absentFields() throws IOException {
        Order empty = new Order(null, "", Status.STATUS_UNSPECIFIED, null, null, null, null, null, null);
        byte[] bytes = JavaProtobuf.encode(empty);
        assertEquals(0, bytes.length);

        Order decoded = JavaProtobuf.decode(bytes, Order.class);
        assertNull(decoded.id());
        assertNull(decoded.discountPercent());
        assertNull(decoded.payment());
        assertEquals("", decoded.customer());
        assertEquals(Status.STATUS_UNSPECIFIED, decoded.status());
        assertEquals(List.of(), decoded.lines());
        assertEquals(Map.of(), decoded.attributes());
    }

    /** The oneof is a sealed interface: a value carries which member is set, and cannot carry two. */
    @Test
    void oneofIsASealedInterface() throws IOException {
        Order invoice = new Order(
            null, "bob", Status.NEW, Instant.EPOCH, null, List.of(), Map.of(), null,
            new Order.Payment.InvoiceRef("INV-7")
        );
        Order decoded = JavaProtobuf.decode(JavaProtobuf.encode(invoice), Order.class);
        assertEquals("invoice INV-7", Main.describe(decoded.payment()));
        assertTrue(decoded.payment() instanceof Order.Payment.InvoiceRef);
        assertEquals("nothing yet", Main.describe(null));
    }

    /** An enum value this build does not know decodes to UNRECOGNIZED instead of failing. */
    @Test
    void unknownEnumValue() throws IOException {
        byte[] bytes = {(byte) 0x18, (byte) 0x63}; // field 3 (status), varint 99
        assertEquals(Status.UNRECOGNIZED, JavaProtobuf.decode(bytes, Order.class).status());
    }
}
