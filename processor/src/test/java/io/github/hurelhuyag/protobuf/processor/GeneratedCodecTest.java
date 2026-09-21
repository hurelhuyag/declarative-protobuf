package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.WireFormat;
import io.github.hurelhuyag.protobuf.DeclarativeProtobuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCodecTest {

    @TempDir
    static Path dir;
    static Compilation compilation;
    static Class<?> orderClass;
    static Class<?> samples;

    static final Descriptor ORDER = TestSchema.message("Order");
    static final Descriptor LINE = TestSchema.message("Line");
    static final Descriptor TREE = TestSchema.message("Tree");

    @BeforeAll
    static void compile() throws Exception {
        Path services = Compilation.servicesDir(dir, Fixtures.SERVICES);
        compilation = Compilation.compile(TestSchema.write(dir), dir, Fixtures.all(), List.of(), services);
        assertTrue(compilation.success(), compilation.messages());
        orderClass = compilation.load("fixture.Order");
        samples = compilation.load("fixture.Samples");
    }

    static Object sample(String name) throws Exception {
        return samples.getMethod(name).invoke(null);
    }

    static Object component(Object record, String name) throws Exception {
        return record.getClass().getMethod(name).invoke(record);
    }

    static FieldDescriptor field(Descriptor message, String name) {
        return message.findFieldByName(name);
    }

    static DynamicMessage line(String sku, int qty, double price) {
        DynamicMessage.Builder b = DynamicMessage.newBuilder(LINE).setField(field(LINE, "sku"), sku);
        if (qty != 0) b.setField(field(LINE, "qty"), qty);
        if (price != 0) b.setField(field(LINE, "price"), price);
        return b.build();
    }

    static DynamicMessage tree(int value, DynamicMessage... children) {
        DynamicMessage.Builder b = DynamicMessage.newBuilder(TREE).setField(field(TREE, "value"), value);
        for (DynamicMessage child : children) b.addRepeatedField(field(TREE, "children"), child);
        return b.build();
    }

    static EnumValueDescriptor status(int number) {
        return TestSchema.DESCRIPTOR.findEnumTypeByName("Status").findValueByNumber(number);
    }

    static DynamicMessage entry(FieldDescriptor mapField, Object key, Object value) {
        Descriptor entry = mapField.getMessageType();
        return DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), key)
            .setField(entry.findFieldByNumber(2), value)
            .build();
    }

    /** The sample order as protobuf-java itself would build and serialize it. */
    static DynamicMessage expectedSample() {
        return DynamicMessage.newBuilder(ORDER)
            .setField(field(ORDER, "id"), 42L)
            .setField(field(ORDER, "customer"), "ann")
            .setField(field(ORDER, "lines"), List.of(line("a", 1, 1.5), line("b", 2, 0)))
            .setField(field(ORDER, "tags"), List.of(1, 2, 300))
            .setField(field(ORDER, "attributes"), List.of(entry(field(ORDER, "attributes"), "k", 7)))
            .setField(field(ORDER, "status"), status(1))
            .setField(field(ORDER, "discount"), 0)
            .setField(
                field(ORDER, "created_at"), Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123).build()
            )
            .setField(field(ORDER, "card"), "visa")
            .setField(field(ORDER, "blob"), ByteString.copyFromUtf8("xyz"))
            .setField(field(ORDER, "primary"), line("p", 3, 2.0))
            .setField(field(ORDER, "history"), List.of(status(1), status(2)))
            .setField(field(ORDER, "delta"), -5)
            .setField(field(ORDER, "hash"), 0xDEADBEEFL)
            .setField(field(ORDER, "flag"), true)
            .setField(field(ORDER, "unsigned"), -1)
            .setField(field(ORDER, "ratio"), 1.5f)
            .setField(field(ORDER, "lines_by_id"), List.of(entry(field(ORDER, "lines_by_id"), 9L, line("q", 1, 0))))
            .setField(field(ORDER, "ttl"), Duration.newBuilder().setSeconds(-1).setNanos(-500_000_000).build())
            .setField(field(ORDER, "maybe"), Int32Value.newBuilder().setValue(0).build())
            .setField(field(ORDER, "notes"), List.of("n1", "n2"))
            .setField(field(ORDER, "status_num"), status(2))
            .setField(field(ORDER, "tree"), tree(1, tree(2), tree(3, tree(4))))
            .build();
    }

    @Test
    void encodesExactlyLikeProtobufJava() throws Exception {
        assertArrayEquals(expectedSample().toByteArray(), DeclarativeProtobuf.encode(sample("order")));
    }

    @Test
    void roundTripsThroughOwnCodec() throws Exception {
        Object order = sample("order");
        assertEquals(order, DeclarativeProtobuf.decode(DeclarativeProtobuf.encode(order), orderClass));
    }

    @Test
    void decodesProtobufJavaOutput() throws Exception {
        assertEquals(sample("order"), DeclarativeProtobuf.decode(expectedSample().toByteArray(), orderClass));
    }

    @Test
    void emptyMessageEncodesToNothingAndDecodesToDefaults() throws Exception {
        Object empty = sample("empty");
        byte[] bytes = DeclarativeProtobuf.encode(empty);
        assertEquals(0, bytes.length);
        assertEquals(empty, DeclarativeProtobuf.decode(bytes, orderClass));
    }

    @Test
    void streamVariants() throws Exception {
        Object order = sample("order");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DeclarativeProtobuf.encode(order, out);
        assertArrayEquals(expectedSample().toByteArray(), out.toByteArray());
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        assertEquals(order, DeclarativeProtobuf.decode(in, orderClass));

        // ByteBuffer input: decodes the remaining bytes, leaves the position alone
        byte[] padded = new byte[out.size() + 5];
        System.arraycopy(out.toByteArray(), 0, padded, 5, out.size());
        ByteBuffer buffer = ByteBuffer.wrap(padded).position(5);
        assertEquals(order, DeclarativeProtobuf.decode(buffer, orderClass));
        assertEquals(5, buffer.position());
    }

    /** bytes as ByteBuffer: read-only on decode, content equality, encode from position to limit. */
    @Test
    void byteBufferSemantics() throws Exception {
        Object decoded = DeclarativeProtobuf.decode(expectedSample().toByteArray(), orderClass);
        ByteBuffer blob = (ByteBuffer) component(decoded, "blob");
        assertTrue(blob.isReadOnly());
        assertEquals(ByteBuffer.wrap("xyz".getBytes()), blob);

        ByteBuffer sliced = ByteBuffer.wrap("__xyz__".getBytes()).position(2).limit(5);
        byte[] bytes = DeclarativeProtobuf.encode(sample("order").getClass().getConstructors()[0].newInstance(
            replaceBlob(sample("order"), sliced)
        ));
        assertEquals(2, sliced.position());
        Object wire = DynamicMessage.parseFrom(ORDER, bytes).getField(field(ORDER, "blob"));
        assertEquals(ByteString.copyFromUtf8("xyz"), wire);
    }

    private static Object[] replaceBlob(Object order, ByteBuffer blob) throws Exception {
        var components = order.getClass().getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            args[i] = components[i].getName().equals("blob") ? blob : components[i].getAccessor().invoke(order);
        }
        return args;
    }

    @Test
    void acceptsUnpackedRepeatedScalars() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeInt32(4, 10);
        out.writeInt32(4, 20);
        out.writeEnum(13, 2);
        out.flush();
        Object decoded = DeclarativeProtobuf.decode(bytes.toByteArray(), orderClass);
        assertEquals(List.of(10, 20), component(decoded, "tags"));
        assertEquals(List.of(Enum.valueOf(compilation.load("fixture.Status").asSubclass(Enum.class), "CLOSED")),
            component(decoded, "history"));
    }

    @Test
    void skipsUnknownFields() throws Exception {
        DynamicMessage withUnknown = expectedSample().toBuilder()
            .setUnknownFields(UnknownFieldSet.newBuilder()
                .addField(999, UnknownFieldSet.Field.newBuilder()
                    .addVarint(5)
                    .addLengthDelimited(ByteString.copyFromUtf8("x"))
                    .build())
                .build())
            .build();
        assertEquals(sample("order"), DeclarativeProtobuf.decode(withUnknown.toByteArray(), orderClass));
    }

    @Test
    void oneofLastValueWinsOnDecode() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeString(9, "visa");
        out.writeInt64(10, 5L);
        out.flush();
        Object decoded = DeclarativeProtobuf.decode(bytes.toByteArray(), orderClass);
        assertNull(component(decoded, "card"));
        assertEquals(5L, component(decoded, "cashCents"));
    }

    @Test
    void rejectsTwoOneofMembersOnEncode() throws Exception {
        Object both = sample("bothPayments");
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class, () -> DeclarativeProtobuf.encode(both)
        );
        assertTrue(e.getMessage().contains("payment"), e.getMessage());
    }

    @Test
    void unknownEnumValueDecodesToUnrecognizedAndCannotBeEncoded() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeEnum(6, 99);
        out.flush();
        Object decoded = DeclarativeProtobuf.decode(bytes.toByteArray(), orderClass);
        assertEquals("UNRECOGNIZED", component(decoded, "status").toString());
        assertThrows(IllegalArgumentException.class, () -> DeclarativeProtobuf.encode(decoded));
    }

    @Test
    void enumBoundToIntKeepsUnknownNumbers() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeEnum(23, 99);
        out.flush();
        assertEquals(99, component(DeclarativeProtobuf.decode(bytes.toByteArray(), orderClass), "statusNum"));
    }

    @Test
    void nestedRecordGetsUnderscoreCodecName() throws Exception {
        Class<?> inner = compilation.load("fixture.Outer$Inner");
        Class<?> outer = compilation.load("fixture.Outer");
        assertEquals("fixture.Outer_InnerProtoCodec", DeclarativeProtobuf.codecClassName(inner));
        Object value = outer.getConstructors()[0].newInstance(inner.getConstructors()[0].newInstance(7));
        assertEquals(value, DeclarativeProtobuf.decode(DeclarativeProtobuf.encode(value), outer));
    }

    @Test
    void partialProjectionWithByteArray() throws Exception {
        Class<?> blobOnly = compilation.load("fixture.BlobOnly");
        Object projected = DeclarativeProtobuf.decode(expectedSample().toByteArray(), blobOnly);
        assertArrayEquals("xyz".getBytes(), (byte[]) component(projected, "blob"));
        assertEquals("ann", component(projected, "customer"));
        DynamicMessage reparsed = DynamicMessage.parseFrom(ORDER, DeclarativeProtobuf.encode(projected));
        assertEquals(ByteString.copyFromUtf8("xyz"), reparsed.getField(field(ORDER, "blob")));
        assertEquals("ann", reparsed.getField(field(ORDER, "customer")));
        assertEquals(0L, reparsed.getField(field(ORDER, "id")));
    }

    /** UUID via the processor-path service file, Percent and LineText via the compile-classpath one. */
    @Test
    void registeredConvertersAndCodecs() throws Exception {
        Class<?> custom = compilation.load("fixture.Custom");
        Class<?> percent = compilation.load("fixture.Percent");
        Object p = percent.getConstructors()[0];
        java.util.UUID id = java.util.UUID.randomUUID();
        Object value = custom.getConstructors()[0].newInstance(
            id, percent.getConstructors()[0].newInstance(0), List.of(id, id),
            java.util.Map.of("k", percent.getConstructors()[0].newInstance(250)), "skuX7".replace('X', 'x'),
            List.of(percent.getConstructors()[0].newInstance(1), percent.getConstructors()[0].newInstance(2))
        );
        byte[] bytes = DeclarativeProtobuf.encode(value);

        DynamicMessage parsed = DynamicMessage.parseFrom(ORDER, bytes);
        assertEquals(id.toString(), parsed.getField(field(ORDER, "customer")));
        assertEquals(0, parsed.getField(field(ORDER, "discount")));            // explicit presence: 0 written
        assertTrue(parsed.hasField(field(ORDER, "discount")));
        assertEquals(List.of(id.toString(), id.toString()), parsed.getField(field(ORDER, "notes")));
        assertEquals(List.of(1, 2), parsed.getField(field(ORDER, "tags")));  // packed via converter
        assertEquals("sku", ((DynamicMessage) parsed.getField(field(ORDER, "primary"))).getField(field(LINE, "sku")));
        assertTrue(parsed.getUnknownFields().asMap().isEmpty());

        assertEquals(value, DeclarativeProtobuf.decode(bytes, custom));
        Object empty = DeclarativeProtobuf.decode(new byte[0], custom);
        assertNull(component(empty, "customer"));      // implicit presence: converter saw "" and returned null
        assertNull(component(empty, "discount"));      // explicit presence: absent stays null, converter not called
        assertNull(component(empty, "primary"));
    }

    @Test
    void codecLookupFailsClearlyForUnboundType() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class, () -> DeclarativeProtobuf.encode("not a record")
        );
        assertTrue(e.getMessage().contains("@ProtoMessage"), e.getMessage());
    }

    @Test
    void truncatedInputFails() throws Exception {
        byte[] bytes = expectedSample().toByteArray();
        byte[] truncated = java.util.Arrays.copyOf(bytes, bytes.length - 3);
        assertThrows(IOException.class, () -> DeclarativeProtobuf.decode(truncated, orderClass));
    }
}
