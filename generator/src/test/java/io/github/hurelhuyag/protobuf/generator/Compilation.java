package io.github.hurelhuyag.protobuf.generator;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Compiles a directory of sources in-process and hands back a class loader over the result. */
record Compilation(boolean success, String messages, ClassLoader loader) {

    static Compilation compile(Path sourceDirectory, Path classesDirectory) throws IOException {
        Files.createDirectories(classesDirectory);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<Path> sources;
        try (Stream<Path> files = Files.walk(sourceDirectory)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).toList();
        }
        boolean success;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classesDirectory));
            List<String> options = List.of(
                "-classpath", System.getProperty("java.class.path"),
                "-Xlint:all", "-Werror" // generated code must compile without a single warning
            );
            success = compiler.getTask(
                null, fileManager, diagnostics, options, null, fileManager.getJavaFileObjectsFromPaths(sources)
            ).call();
        }
        StringBuilder text = new StringBuilder();
        diagnostics.getDiagnostics().forEach(d -> text.append(d.getKind()).append(": ").append(d).append('\n'));
        ClassLoader loader = new URLClassLoader(
            new URL[]{classesDirectory.toUri().toURL()}, Compilation.class.getClassLoader()
        );
        return new Compilation(success, text.toString(), loader);
    }

    Class<?> load(String name) {
        try {
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("class " + name + " not compiled:\n" + messages, e);
        }
    }

    Object call(String className, String method, Class<?>[] types, Object... args) throws Exception {
        return load(className).getMethod(method, types).invoke(null, args);
    }
}
