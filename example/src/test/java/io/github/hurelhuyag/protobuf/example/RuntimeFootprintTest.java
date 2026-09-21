package io.github.hurelhuyag.protobuf.example;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves what the runtime classpath of a consumer needs: nothing protoc generated, and from protobuf only classes
 * that {@code protobuf-javalite} provides. Runs jdeps over this module's compiled classes (records + generated
 * codecs) against the test classpath, which holds the api and javalite but not protobuf-java.
 */
class RuntimeFootprintTest {

    @Test
    void noProtocGeneratedClasses() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("target/classes"))) {
            List<String> classes = files.map(Path::toString).filter(p -> p.endsWith(".class")).toList();
            assertFalse(classes.isEmpty());
            for (String cls : classes) {
                assertTrue(cls.contains("io/github/hurelhuyag/protobuf/example/"), "unexpected class " + cls);
            }
        }
        assertEquals(List.of(), listing("target/generated-sources/protobuf"), "protoc must not generate Java here");
    }

    @Test
    void everyProtobufReferenceResolvesToJavalite() {
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").orElseThrow();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int status = jdeps.run(
            new PrintWriter(out), new PrintWriter(err),
            "-verbose:class", "--multi-release", "17", "-cp", System.getProperty("java.class.path"), "target/classes"
        );
        assertEquals(0, status, err.toString());
        List<String> protobufLines = out.toString().lines().filter(l -> l.contains("com.google.protobuf")).toList();
        assertFalse(protobufLines.isEmpty(), out.toString());
        for (String line : protobufLines) {
            assertTrue(line.contains("protobuf-javalite"), "not provided by protobuf-javalite: " + line.trim());
        }
        assertFalse(out.toString().contains("not found"), out.toString());
    }

    private static List<String> listing(String dir) throws Exception {
        Path path = Path.of(dir);
        if (!Files.isDirectory(path)) return List.of();
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(Files::isRegularFile).map(Path::toString).toList();
        }
    }
}
