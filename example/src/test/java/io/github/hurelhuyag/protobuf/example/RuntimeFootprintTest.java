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
 * What a consumer's runtime classpath actually needs: nothing protoc generated, and from protobuf only classes
 * that {@code protobuf-javalite} provides.
 */
class RuntimeFootprintTest {

    @Test
    void onlyGeneratedRecordsAndCodecs() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("target/classes"))) {
            List<String> classes = files.map(Path::toString).filter(path -> path.endsWith(".class")).toList();
            assertFalse(classes.isEmpty());
            for (String name : classes) {
                assertTrue(name.contains("io/github/hurelhuyag/protobuf/example/"), "unexpected class " + name);
            }
        }
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
        List<String> protobufLines = out.toString().lines()
            .filter(line -> line.contains("com.google.protobuf"))
            .toList();
        assertFalse(protobufLines.isEmpty(), out.toString());
        for (String line : protobufLines) {
            assertTrue(line.contains("protobuf-javalite"), "not provided by protobuf-javalite: " + line.trim());
        }
        assertFalse(out.toString().contains("not found"), out.toString());
    }
}
