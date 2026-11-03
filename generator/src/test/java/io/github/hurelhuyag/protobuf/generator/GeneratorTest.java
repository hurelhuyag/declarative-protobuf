package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generates the whole of {@link TestSchema}, compiles it, and checks the result against protobuf-java: the same
 * schema parsed by {@link DynamicMessage} must understand every byte we write, with nothing left unknown.
 */
class GeneratorTest {

    @TempDir
    static Path dir;
    static Compilation compilation;
    static Map<String, String> sources;

    static final Descriptor EVERYTHING = TestSchema.message("Everything");

    @BeforeAll
    static void generateAndCompile() throws Exception {
        sources = Generator.generate(TestSchema.set(), List.of("demo.proto"));
        Path sourceDirectory = dir.resolve("generated");
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = sourceDirectory.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
        for (String handWritten : List.of("Driver", "UuidConverter", "MillisConverter")) {
            Files.copy(
                Path.of("src/test/java/io/github/hurelhuyag/protobuf/generator/" + handWritten + ".java.txt"),
                sourceDirectory.resolve("demo/" + handWritten + ".java")
            );
        }
        compilation = Compilation.compile(sourceDirectory, dir.resolve("classes"));
        assertTrue(compilation.success(), compilation.messages());
    }

    static byte[] bytes(String method) throws Exception {
        return (byte[]) compilation.call("demo.Driver", method, new Class<?>[0]);
    }

    @Test
    void generatesOneRecordPerMessageAndOneEnumPerEnum() {
        assertEquals(
            List.of(
                "demo/Color.java",
                "demo/Uuid.java", "demo/UuidProtoCodec.java",
                "demo/Point.java", "demo/PointProtoCodec.java",
                "demo/Tree.java", "demo/TreeProtoCodec.java",
                "demo/Segment.java", "demo/SegmentProtoCodec.java",
                "demo/Everything.java", "demo/EverythingProtoCodec.java", "demo/Everything_NestedProtoCodec.java"
            ),
            List.copyOf(sources.keySet())
        );
    }

    /** protobuf-java parses our bytes with the same schema: nothing unknown, and re-serialising is byte-identical. */
    @Test
    void protobufJavaUnderstandsEveryByte() throws Exception {
        byte[] encoded = bytes("encodeSample");
        DynamicMessage parsed = DynamicMessage.parseFrom(EVERYTHING, encoded);
        assertTrue(parsed.getUnknownFields().asMap().isEmpty(), parsed.getUnknownFields().toString());
        assertArrayEquals(encoded, parsed.toByteArray());
        assertEquals("text", parsed.getField(EVERYTHING.findFieldByName("string_value")));
        assertEquals(-3, parsed.getField(EVERYTHING.findFieldByName("int32_value")));
        assertEquals("circle", parsed.getField(EVERYTHING.findFieldByName("label")));
        assertEquals(List.of(1, -2, 300), parsed.getField(EVERYTHING.findFieldByName("ints")));
        assertTrue(parsed.hasField(EVERYTHING.findFieldByName("opt_int32")));   // present although it holds 0
    }

    /**
     * A converter named by the schema: the record carries {@link java.util.UUID} and {@link java.time.Instant},
     * the wire still carries the {@code Uuid} message and an {@code int64}.
     */
    @Test
    void convertersNamedBySchemaOptions() throws Exception {
        byte[] encoded = bytes("encodeSample");
        DynamicMessage parsed = DynamicMessage.parseFrom(EVERYTHING, encoded);
        DynamicMessage id = (DynamicMessage) parsed.getField(EVERYTHING.findFieldByName("request_id"));
        Descriptor uuid = TestSchema.message("Uuid");
        assertEquals(0x123e4567e89b12d3L, id.getField(uuid.findFieldByName("msb")));
        assertEquals(1_700_000_000_123L, parsed.getField(EVERYTHING.findFieldByName("created_millis")));

        assertEquals(
            "123e4567-e89b-12d3-a456-426614174000"
                + "|[00000000-0000-0000-0000-000000000001]"
                + "|2023-11-14T22:13:20.123Z"
                + "|{ann=00000000-0000-0000-0000-000000000002}",
            compilation.call("demo.Driver", "describeUuids", new Class<?>[]{byte[].class}, (Object) encoded)
        );
    }

    @Test
    void roundTripsThroughItsOwnCodec() throws Exception {
        assertEquals(Boolean.TRUE, compilation.call("demo.Driver", "roundTrips", new Class<?>[0]));
    }

    @Test
    void aMessageOfDefaultsIsEmptyOnTheWire() throws Exception {
        assertEquals(0, bytes("encodeEmpty").length);
        assertEquals(Boolean.TRUE, compilation.call("demo.Driver", "nullCollectionsBecomeEmpty", new Class<?>[0]));
    }

    /** Every singular field without {@code optional} is required: the record cannot be built holding null. */
    @Test
    void nonOptionalFieldsRejectNull() throws Exception {
        assertEquals("demo.Tree.name is not optional", compilation.call(
            "demo.Driver", "rejectNull", new Class<?>[]{String.class}, "string"
        ));
        assertEquals("demo.Everything.Nested.kind is not optional", compilation.call(
            "demo.Driver", "rejectNull", new Class<?>[]{String.class}, "enum"
        ));
        assertEquals("demo.Segment.start is not optional", compilation.call(
            "demo.Driver", "rejectNull", new Class<?>[]{String.class}, "message"
        ));
        assertEquals("demo.Everything.color is not optional", compilation.call(
            "demo.Driver", "rejectNull", new Class<?>[]{String.class}, "everything"
        ));
    }

    @Test
    void aMissingRequiredMessageFieldFailsDecoding() throws Exception {
        assertEquals(
            "demo.Segment.start is not optional",
            compilation.call("demo.Driver", "decodeEmptySegment", new Class<?>[0])
        );
    }

    /** A oneof member is set by construction, so it is written even when it carries the type's default value. */
    @Test
    void oneofCasesAreWrittenEvenWhenTheyHoldDefaults() throws Exception {
        for (String which : List.of("label", "radius", "center")) {
            byte[] encoded = (byte[]) compilation.call(
                "demo.Driver", "encodeShape", new Class<?>[]{String.class}, which
            );
            DynamicMessage parsed = DynamicMessage.parseFrom(EVERYTHING, encoded);
            assertTrue(parsed.hasField(EVERYTHING.findFieldByName(which.equals("center") ? "center" : which)), which);
            assertEquals(
                which + ":" + (which.equals("label") ? "" : "0"),
                compilation.call("demo.Driver", "describeShape", new Class<?>[]{byte[].class}, (Object) encoded)
            );
        }
    }

    @Test
    void lastOneofMemberOnTheWireWins() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeString(27, "first");
        out.writeInt32(28, 7);
        out.flush();
        assertEquals("radius:7", compilation.call(
            "demo.Driver", "describeShape", new Class<?>[]{byte[].class}, (Object) bytes.toByteArray()
        ));
    }

    @Test
    void unknownEnumValueDecodesToUnrecognizedAndCannotBeEncoded() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeEnum(18, 99);
        out.flush();
        assertEquals("UNRECOGNIZED", compilation.call(
            "demo.Driver", "colorName", new Class<?>[]{byte[].class}, (Object) bytes.toByteArray()
        ));
        assertEquals(
            "demo.Color.UNRECOGNIZED cannot be encoded",
            compilation.call("demo.Driver", "encodeUnrecognized", new Class<?>[0])
        );
    }

    @Test
    void decodesWhatProtobufJavaWrites() throws Exception {
        DynamicMessage message = DynamicMessage.newBuilder(EVERYTHING)
            .setField(EVERYTHING.findFieldByName("string_value"), "from protoc")
            .setField(EVERYTHING.findFieldByName("radius"), 42)
            .build();
        assertEquals("radius:42", compilation.call(
            "demo.Driver", "describeShape", new Class<?>[]{byte[].class}, (Object) message.toByteArray()
        ));
    }

    /** Interfaces and annotations the build asks for land on the matching records, nested ones included. */
    @Test
    void customizationsFromTheBuild(@TempDir Path customized) throws Exception {
        Map<String, String> generated = Generator.generate(
            TestSchema.set(), List.of("demo.proto"), null, new Customizations(
                List.of(
                    new Customizations.Rule("demo.Tree", "demo.Named"),
                    new Customizations.Rule("demo.Point", "java.io.Serializable")
                ),
                List.of(new Customizations.Rule("demo.*", "demo.Marker"))
            )
        );
        Path sourceDirectory = customized.resolve("generated");
        for (Map.Entry<String, String> source : generated.entrySet()) {
            Path file = sourceDirectory.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
        Files.writeString(
            sourceDirectory.resolve("demo/Named.java"), "package demo; public interface Named { String name(); }"
        );
        Files.writeString(sourceDirectory.resolve("demo/Marker.java"), "package demo; public @interface Marker {}");
        for (String handWritten : List.of("UuidConverter", "MillisConverter")) {
            Files.copy(
                Path.of("src/test/java/io/github/hurelhuyag/protobuf/generator/" + handWritten + ".java.txt"),
                sourceDirectory.resolve("demo/" + handWritten + ".java")
            );
        }
        Compilation compiled = Compilation.compile(sourceDirectory, customized.resolve("classes"));
        assertTrue(compiled.success(), compiled.messages());

        assertTrue(generated.get("demo/Tree.java").contains(") implements Named {"), generated.get("demo/Tree.java"));
        assertTrue(generated.get("demo/Point.java").contains("import java.io.Serializable;"));
        assertTrue(generated.get("demo/Point.java").contains(") implements Serializable {"));
        assertTrue(generated.get("demo/Everything.java").contains("    @Marker\n    public record Nested("));
    }

    @Test
    void aCustomizationThatMatchesNothingFailsTheBuild() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Generator.generate(
            TestSchema.set(), List.of("demo.proto"), null, new Customizations(
                List.of(new Customizations.Rule("demo.Pointt", "java.io.Serializable")), List.of()
            )
        ));
        assertEquals("'demo.Pointt' (for java.io.Serializable) matches no generated message", e.getMessage());
    }

    @Test
    void unpackedRepeatedScalarsAreAccepted() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeInt32(21, 10);
        out.writeInt32(21, 20);
        out.flush();
        assertEquals("[10, 20]", compilation.call(
            "demo.Driver", "describeInts", new Class<?>[]{byte[].class}, (Object) bytes.toByteArray()
        ));
    }
}
