package io.github.hurelhuyag.protobuf.example;

import io.github.hurelhuyag.protobuf.JavaProtobuf;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /** Absent fields come back as null (optional) or empty (collections), never as a surprise. */
    @Test
    void absentFields() throws IOException {
        Order minimal = new Order(
            new UUID(0, 0), "", Status.STATUS_UNSPECIFIED, Instant.EPOCH, null, null, null, null, null
        );
        Order decoded = JavaProtobuf.decode(JavaProtobuf.encode(minimal), Order.class);
        assertEquals(minimal, decoded);
        assertNull(decoded.shipping());
        assertNull(decoded.discountPercent());
        assertNull(decoded.payment());
        assertEquals("", decoded.customer());
        assertEquals(Status.STATUS_UNSPECIFIED, decoded.status());
        assertEquals(List.of(), decoded.lines());
        assertEquals(Map.of(), decoded.attributes());
    }

    /** A field without {@code optional} is required: the record cannot be built without it. */
    @Test
    void requiredFieldRejectsNull() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> new Order(
            null, "ann", Status.NEW, Instant.EPOCH, null, List.of(), Map.of(), null, null
        ));
        assertEquals("example.orders.Order.id is not optional", e.getMessage());
    }

    /** Bytes missing a required message field do not decode into a record that breaks its own schema. */
    @Test
    void requiredFieldMissingOnTheWire() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> JavaProtobuf.decode(
            new byte[0], Order.class
        ));
        assertEquals("example.orders.Order.id is not optional", e.getMessage());
    }

    /** The oneof is a sealed interface: a value carries which member is set, and cannot carry two. */
    @Test
    void oneofIsASealedInterface() throws IOException {
        Order invoice = new Order(
            new UUID(0, 7), "bob", Status.NEW, Instant.EPOCH, null, List.of(), Map.of(), null,
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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JavaProtobuf.encode(bytes, Main.sample());
        bytes.write(new byte[]{(byte) 0x18, (byte) 0x63}); // field 3 (status), varint 99: the last value wins
        assertEquals(Status.UNRECOGNIZED, JavaProtobuf.decode(bytes.toByteArray(), Order.class).status());
    }
}
