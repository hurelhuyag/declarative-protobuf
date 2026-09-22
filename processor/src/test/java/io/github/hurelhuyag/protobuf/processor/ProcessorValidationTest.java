package io.github.hurelhuyag.protobuf.processor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorValidationTest {

    @TempDir
    static Path dir;
    static Path descriptors;

    @BeforeAll
    static void schema() throws IOException {
        descriptors = TestSchema.write(dir);
    }

    static final String HEADER = """
        package bad;

        import io.github.hurelhuyag.protobuf.*;
        import java.util.*;

        """;

    /** Compiles Status + Line + codec fixtures + one bad record and returns the diagnostics; asserts failure. */
    static String failing(String recordSource) throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture.Status", Fixtures.STATUS);
        sources.put("fixture.Line", Fixtures.LINE);

        sources.put("bad.Bad", HEADER + recordSource);
        Compilation compilation = Compilation.compile(descriptors, Files.createTempDirectory(dir, "case"), sources);
        assertFalse(compilation.success(), "expected compilation to fail:\n" + compilation.messages());
        return compilation.messages();
    }

    /** Like {@link #failing(String)} with META-INF/services entries on the compile classpath. */
    static String failing(String recordSource, Map<String, String> services) throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture.Status", Fixtures.STATUS);
        sources.put("fixture.Line", Fixtures.LINE);
        sources.put("fixture.LineTextCodec", Fixtures.LINE_TEXT_CODEC);
        sources.put("fixture.CentsCodec", Fixtures.CENTS_CODEC);
        sources.put("bad.Bad", HEADER + recordSource);
        Path work = Files.createTempDirectory(dir, "case");
        Compilation compilation = Compilation.compile(
            descriptors, work, sources, List.of(), Compilation.servicesDir(work, services)
        );
        assertFalse(compilation.success(), "expected compilation to fail:\n" + compilation.messages());
        return compilation.messages();
    }

    /** A record bound to test.Order with the given components. */
    static String bound(String components) {
        return "@ProtoMessage(\"test.Order\") public record Bad(" + components + ") {}";
    }

    static void assertMentions(String messages, String... expected) {
        for (String e : expected) assertTrue(messages.contains(e), "expected '" + e + "' in:\n" + messages);
    }

    @Test
    void primitiveForOptionalField() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(7) int discount) {}");
        assertMentions(m, "test.Order.discount", "optional int32", "'int'", "'java.lang.Integer'", "explicit presence");
    }

    @Test
    void boxedForImplicitPresenceField() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(1) Long id) {}");
        assertMentions(m, "'int64'", "'long'", "implicit presence");
    }

    @Test
    void primitiveForOneofMember() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(10) long cashCents) {}");
        assertMentions(m, "oneof payment", "'java.lang.Long'");
    }

    @Test
    void wrongScalarType() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(2) int customer) {}");
        assertMentions(m, "'string'", "'java.lang.String'");
    }

    @Test
    void unknownFieldNumber() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(99) long id) {}");
        assertMentions(m, "no field number 99");
    }

    @Test
    void reservedFieldNumber() throws IOException {
        String m = failing("@ProtoMessage(\"test.Line\") public record Bad(@Proto(5) long legacy) {}");
        assertMentions(m, "no field number 5 (it is reserved)");
    }

    @Test
    void reservedEnumNumber() throws IOException {
        String m = failing("""
            @ProtoMessage("test.Order") public record Bad(@Proto(6) Reserved status) {}
            @ProtoEnum("test.Status") enum Reserved { @Proto(0) A, @Proto(1) B, @Proto(2) C, @Proto(11) D }
            """);
        assertMentions(m, "has no value 11 (it is reserved)");
    }

    @Test
    void duplicateFieldNumber() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(1) long id, @Proto(1) long id2) {}");
        assertMentions(m, "already bound to component 'id'");
    }

    @Test
    void missingProtoAnnotation() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(1) long id, String customer) {}");
        assertMentions(m, "component 'customer' has no @Proto");
    }

    @Test
    void unknownMessage() throws IOException {
        String m = failing("@ProtoMessage(\"test.Nope\") public record Bad(@Proto(1) long id) {}");
        assertMentions(m, "no message 'test.Nope'");
    }

    @Test
    void bareAnnotationWithNoMatch() throws IOException {
        // no message in the schema is called Bad, and bad.Bad is nobody's Java name
        String m = failing("@ProtoMessage public record Bad(@Proto(1) long id) {}");
        assertMentions(m, "no message with Java name 'bad.Bad'", "nor a unique message named 'Bad'");
    }

    @Test
    void bareAnnotationAmbiguousSimpleName() throws IOException {
        // two messages are called Line (test.Line, other.Line) and bad.Line matches neither Java name
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("bad.Line", HEADER + "@ProtoMessage public record Line(@Proto(1) String name) {}");
        Compilation c = Compilation.compile(descriptors, Files.createTempDirectory(dir, "amb"), sources);
        assertFalse(c.success());
        assertMentions(c.messages(), "'Line' is ambiguous: [test.Line, other.Line]");
    }

    @Test
    void repeatedNeedsList() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(4) int[] tags) {}");
        assertMentions(m, "repeated int32", "java.util.List<E>");
    }

    @Test
    void repeatedElementTypeChecked() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(4) List<Long> tags) {}");
        assertMentions(m, "'java.lang.Integer'");
    }

    @Test
    void mapNeedsMap() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(5) List<String> attributes) {}");
        assertMentions(m, "map<string, int32>", "java.util.Map<K, V>");
    }

    @Test
    void messageFieldNeedsBoundRecord() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(12) String primary) {}");
        assertMentions(m, "a @ProtoMessage record bound to 'test.Line'");
    }

    @Test
    void messageFieldBoundToWrongMessage() throws IOException {
        String m = failing("""
            @ProtoMessage("test.Order") public record Bad(@Proto(12) Other primary) {}
            @ProtoMessage("test.Tree") record Other(@Proto(1) int value) {}
            """);
        assertMentions(m, "bound to 'test.Line'", "bound to 'test.Tree'");
    }

    @Test
    void wellKnownTypeAlternativesListed() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public record Bad(@Proto(8) long createdAt) {}");
        assertMentions(m, "google.protobuf.Timestamp", "'java.time.Instant'");
    }

    @Test
    void enumMissingValueConstant() throws IOException {
        String m = failing("""
            @ProtoMessage("test.Order") public record Bad(@Proto(6) Partial status) {}
            @ProtoEnum("test.Status") enum Partial { @Proto(0) A, @Proto(1) B }
            """);
        assertMentions(m, "CLOSED = 2", "@Proto(2)");
    }

    @Test
    void enumConstantNotInSchema() throws IOException {
        String m = failing("""
            @ProtoMessage("test.Order") public record Bad(@Proto(6) Extra status) {}
            @ProtoEnum("test.Status") enum Extra { @Proto(0) A, @Proto(1) B, @Proto(2) C, @Proto(3) D }
            """);
        assertMentions(m, "has no value 3");
    }

    @Test
    void enumWithoutUnrecognizedConstantWarns() throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("warn.Closed", """
            package warn;
            import io.github.hurelhuyag.protobuf.*;
            @ProtoEnum("test.Status") public enum Closed { @Proto(0) A, @Proto(1) B, @Proto(2) C }
            """);
        Compilation compilation = Compilation.compile(descriptors, Files.createTempDirectory(dir, "warn"), sources);
        // -Werror turns the warning into a failure, which is what we assert on
        assertFalse(compilation.success());
        assertMentions(compilation.messages(), "@ProtoUnrecognized");
    }

    @Test
    void ambiguousRegisteredCodecs() throws IOException {
        // ServiceUuidCodec (processor path) and this one (classpath services file) both convert string <-> UUID
        String m = failing("""
            @ProtoMessage("test.Order") public record Bad(@Proto(2) java.util.UUID customer) {}
            """ + OTHER_UUID_CODEC, Map.of("io.github.hurelhuyag.protobuf.ProtoConverter", "bad.OtherUuidCodec\n"));
        assertMentions(m, "several registered converters fit component 'customer'", "bad.OtherUuidCodec");
    }

    static final String OTHER_UUID_CODEC = """
        class OtherUuidCodec implements ProtoConverter<String, java.util.UUID> {
            public java.util.UUID fromWire(String w) { return java.util.UUID.fromString(w); }
            public String toWire(java.util.UUID v) { return v.toString(); }
        }
        """;

    @Test
    void registeredCodecIsIgnoredWhereItDoesNotFit() throws IOException {
        // ServiceUuidCodec converts string; on an int64 field a UUID component is simply unbound
        String m = failing(bound("@Proto(1) java.util.UUID id"));
        assertMentions(m, "'int64'", "component 'id' is 'java.util.UUID'");
    }

    @Test
    void registeredCodecNeedsAccessibleNoArgConstructor() throws IOException {
        String m = failing("""
            @ProtoMessage("test.Order") public record Bad(@Proto(1) java.math.BigDecimal id) {}
            class NoCtor implements ProtoConverter<Long, java.math.BigDecimal> {
                NoCtor(int x) {}
                public java.math.BigDecimal fromWire(Long w) { return null; }
                public Long toWire(java.math.BigDecimal v) { return 0L; }
            }
            """, Map.of("io.github.hurelhuyag.protobuf.ProtoConverter", "bad.NoCtor\n"));
        assertMentions(m, "needs a no-arg constructor accessible from package");
    }

    @Test
    void registeredClassMustImplementProtoConverter() throws IOException {
        String m = failing(bound("@Proto(1) long id"),
            Map.of("io.github.hurelhuyag.protobuf.ProtoConverter", "java.lang.String\n"));
        assertMentions(m, "'java.lang.String' does not implement ProtoConverter");
    }

    @Test
    void messageConverterMustHaveRecordBoundToThatMessage() throws IOException {
        // LineTextCodec maps Line <-> String; field 24 is a Tree, so a String there is simply unbound
        String m = failing(bound("@Proto(24) String tree"));
        assertMentions(m, "'test.Tree'", "component 'tree' is 'java.lang.String'");
    }

    @Test
    void notARecord() throws IOException {
        String m = failing("@ProtoMessage(\"test.Order\") public class Bad {}");
        assertMentions(m, "only supported on records");
    }

    @Test
    void missingDescriptorOption() throws IOException {
        Map<String, String> sources = Map.of("bad.Bad", HEADER + "@ProtoMessage public record Bad() {}");
        Compilation compilation = Compilation.compile(Path.of(""), Files.createTempDirectory(dir, "opt"), sources);
        assertFalse(compilation.success());
        assertMentions(compilation.messages(), DeclarativeProtobufProcessor.DESCRIPTORS_OPTION);
    }
}
