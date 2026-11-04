package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
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
        sources = Generator.generate(TestSchema.set(), List.of("demo.proto"), null, new Customizations(
            List.of(), List.of(), List.of(
                new Customizations.Conversion("demo.Uuid", "java.util.UUID", "demo.UuidConverter"),
                new Customizations.Conversion(
                    "demo.Everything.created_millis", "java.time.Instant", "demo.MillisConverter"
                )
            )
        ));
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
                "demo/ProtoUuid.java", "demo/ProtoUuidProtoCodec.java",
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
     * Converters the build asked for: the record carries {@link java.util.UUID} and {@link java.time.Instant},
     * the wire still carries the {@code Uuid} message and an {@code int64}.
     */
    @Test
    void conversionsFromTheBuild() throws Exception {
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
                List.of(new Customizations.Rule("demo.*", "demo.Marker")),
                List.of()
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
                List.of(new Customizations.Rule("demo.Pointt", "java.io.Serializable")), List.of(), List.of()
            )
        ));
        assertEquals("'demo.Pointt' (for java.io.Serializable) matches no generated message", e.getMessage());
    }

    /** Without a conversion, a message the build could have converted is an ordinary generated record. */
    @Test
    void withoutAConversionAMessageIsItsRecord() throws Exception {
        Map<String, String> generated = Generator.generate(TestSchema.set(), List.of("demo.proto"));
        assertTrue(
            generated.get("demo/Everything.java").contains("List<Uuid> relatedIds"),
            generated.get("demo/Everything.java")
        );
        assertTrue(generated.get("demo/Everything.java").contains("long createdMillis"));
    }

    /** A converted message's record is prefixed only while it is converted: a schema type next to it may not clash. */
    @Test
    void aPrefixedRecordNameTakenByTheSchemaFailsTheBuild() {
        FileDescriptorSet set = TestSchema.set().toBuilder()
            .setFile(3, TestSchema.FILE.toBuilder().addMessageType(DescriptorProto.newBuilder().setName("ProtoUuid")))
            .build();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Generator.generate(
            set, List.of("demo.proto"), null, new Customizations(
                List.of(), List.of(), List.of(new Customizations.Conversion("demo.Uuid", "java.util.UUID", "demo.C"))
            )
        ));
        assertEquals(
            "'demo.Uuid' is converted, so its record is named ProtoUuid, but the schema declares a ProtoUuid next"
                + " to it",
            e.getMessage()
        );
    }

    /** A conversion of a map field, a map entry or a name that is not in the schema at all is a build failure. */
    @Test
    void aConversionThatNamesNothingConvertibleFailsTheBuild() {
        for (String target : List.of("demo.Uuidd", "demo.Everything.owners", "demo.Everything.OwnersEntry")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Generator.generate(
                TestSchema.set(), List.of("demo.proto"), null, new Customizations(
                    List.of(), List.of(), List.of(new Customizations.Conversion(target, "java.util.UUID", "demo.C"))
                )
            ));
            assertEquals(
                "'" + target + "' (for demo.C) names no message or field that can be converted", e.getMessage()
            );
        }
    }

    @Test
    void aTargetConvertedTwiceFailsTheBuild() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Generator.generate(
            TestSchema.set(), List.of("demo.proto"), null, new Customizations(
                List.of(), List.of(), List.of(
                    new Customizations.Conversion("demo.Uuid", "java.util.UUID", "demo.UuidConverter"),
                    new Customizations.Conversion("demo.Uuid", "java.lang.String", "demo.UuidStrings")
                )
            )
        ));
        assertEquals("'demo.Uuid' is converted more than once", e.getMessage());
    }

    @Test
    void parsesAConversionOption() {
        assertEquals(
            new Customizations.Conversion("demo.Uuid", "java.util.UUID", "demo.UuidConverter"),
            Customizations.Conversion.parse("demo.Uuid=java.util.UUID:demo.UuidConverter")
        );
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class, () -> Customizations.Conversion.parse("demo.Uuid=java.util.UUID")
        );
        assertEquals(
            "--convert expects <message or field>=<java type>:<converter>, got 'demo.Uuid=java.util.UUID'",
            e.getMessage()
        );
    }

    /**
     * A converted message whose own fields are converted: the {@code ProtoSegment} record already holds the
     * application's {@code Xy} values, so the segment converter maps application types only, and the bytes are
     * still the schema's {@code Segment} of two {@code Point}s.
     */
    @Test
    void aConvertedMessageCanHoldConvertedValues(@TempDir Path nested) throws Exception {
        Map<String, String> generated = Generator.generate(
            TestSchema.set(), List.of("demo.proto"), null, new Customizations(
                List.of(), List.of(), List.of(
                    new Customizations.Conversion("demo.Point", "demo.Xy", "demo.XyConverter"),
                    new Customizations.Conversion("demo.Segment", "demo.Line", "demo.LineConverter")
                )
            )
        );
        Path sourceDirectory = nested.resolve("generated");
        for (Map.Entry<String, String> source : generated.entrySet()) {
            Path file = sourceDirectory.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
        Files.writeString(sourceDirectory.resolve("demo/Xy.java"), "package demo; public record Xy(int x, int y) {}");
        Files.writeString(
            sourceDirectory.resolve("demo/Line.java"), "package demo; public record Line(Xy from, Xy to) {}"
        );
        Files.writeString(sourceDirectory.resolve("demo/XyConverter.java"), """
            package demo;
            public final class XyConverter implements io.github.hurelhuyag.protobuf.ProtoConverter<ProtoPoint, Xy> {
                public Xy fromWire(ProtoPoint wire) { return new Xy(wire.x(), wire.y()); }
                public ProtoPoint toWire(Xy value) { return new ProtoPoint(value.x(), value.y()); }
            }
            """);
        Files.writeString(sourceDirectory.resolve("demo/LineConverter.java"), """
            package demo;
            import io.github.hurelhuyag.protobuf.ProtoConverter;
            public final class LineConverter implements ProtoConverter<ProtoSegment, Line> {
                public Line fromWire(ProtoSegment wire) { return new Line(wire.start(), wire.end()); }
                public ProtoSegment toWire(Line value) { return new ProtoSegment(value.from(), value.to()); }
            }
            """);
        Files.writeString(sourceDirectory.resolve("demo/NestedDriver.java"), """
            package demo;
            import io.github.hurelhuyag.protobuf.JavaProtobuf;
            public final class NestedDriver {
                public static byte[] encode() {
                    return JavaProtobuf.encode(new LineConverter().toWire(new Line(new Xy(1, 2), new Xy(3, 4))));
                }
                public static String decode(byte[] bytes) throws Exception {
                    return new LineConverter().fromWire(JavaProtobuf.decode(bytes, ProtoSegment.class)).toString();
                }
            }
            """);
        Compilation compiled = Compilation.compile(sourceDirectory, nested.resolve("classes"));
        assertTrue(compiled.success(), compiled.messages());

        assertTrue(
            generated.get("demo/ProtoSegment.java").contains("Xy start,"), generated.get("demo/ProtoSegment.java")
        );
        byte[] encoded = (byte[]) compiled.call("demo.NestedDriver", "encode", new Class<?>[0]);
        Descriptor segment = TestSchema.message("Segment");
        Descriptor point = TestSchema.message("Point");
        DynamicMessage parsed = DynamicMessage.parseFrom(segment, encoded);
        DynamicMessage end = (DynamicMessage) parsed.getField(segment.findFieldByName("end"));
        assertEquals(3, end.getField(point.findFieldByName("x")));
        assertEquals(4, end.getField(point.findFieldByName("y")));
        assertEquals(
            "Line[from=Xy[x=1, y=2], to=Xy[x=3, y=4]]",
            compiled.call("demo.NestedDriver", "decode", new Class<?>[]{byte[].class}, (Object) encoded)
        );
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
