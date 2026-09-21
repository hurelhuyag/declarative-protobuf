package io.github.hurelhuyag.protobuf.processor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compiles fixture sources in-process with the processor attached. */
record Compilation(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, ClassLoader loader) {

    static Compilation compile(Path descriptors, Path workDir, Map<String, String> sources) throws IOException {
        return compile(descriptors, workDir, sources, List.of());
    }

    static Compilation compile(Path descriptors, Path workDir, Map<String, String> sources, List<String> extraOptions)
        throws IOException {
        return compile(descriptors, workDir, sources, extraOptions, null);
    }

    /**
     * @param extraClasspath a compile classpath entry put first, like Maven puts the module's own target/classes
     *                       (e.g. a directory holding META-INF/services)
     */
    static Compilation compile(
        Path descriptors, Path workDir, Map<String, String> sources, List<String> extraOptions, Path extraClasspath
    ) throws IOException {
        Path classes = Files.createDirectories(workDir.resolve("classes"));
        Path generated = Files.createDirectories(workDir.resolve("generated"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generated));
            List<JavaFileObject> units = new ArrayList<>();
            sources.forEach((name, code) -> units.add(new SimpleJavaFileObject(
                URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE
            ) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return code;
                }
            }));
            String classpath = (extraClasspath == null ? "" : extraClasspath + java.io.File.pathSeparator)
                + System.getProperty("java.class.path");
            List<String> options = new ArrayList<>(List.of(
                "-classpath", classpath,
                "-processorpath", System.getProperty("java.class.path"),
                "-processor", DeclarativeProtobufProcessor.class.getName(),
                "-A" + DeclarativeProtobufProcessor.DESCRIPTORS_OPTION + "=" + descriptors,
                "-Xlint:all,-processing", "-Werror" // generated code must be warning-free
            ));
            options.addAll(extraOptions);
            boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            ClassLoader loader = new URLClassLoader(
                new java.net.URL[]{classes.toUri().toURL()}, Compilation.class.getClassLoader()
            );
            return new Compilation(success, diagnostics.getDiagnostics(), loader);
        }
    }

    /** Writes META-INF/services files into a fresh directory suitable as an extra classpath entry. */
    static Path servicesDir(Path workDir, Map<String, String> services) throws IOException {
        Path dir = Files.createDirectories(workDir.resolve("services-cp/META-INF/services"));
        for (Map.Entry<String, String> e : services.entrySet()) {
            Files.writeString(dir.resolve(e.getKey()), e.getValue());
        }
        return workDir.resolve("services-cp");
    }

    String messages() {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<?> d : diagnostics) sb.append(d.getKind()).append(": ").append(d.getMessage(null)).append('\n');
        return sb.toString();
    }

    Class<?> load(String name) {
        try {
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("class " + name + " not compiled:\n" + messages(), e);
        }
    }
}
