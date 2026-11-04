package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Two ways to run the generator:
 * <ul>
 *   <li>{@code java -jar generator.jar [option...] <descriptor-set.pb> <output-dir> [proto-file...]} — reads a
 *       descriptor set written by {@code protoc --descriptor_set_out=... --include_imports} and writes sources to a
 *       directory. The options, each but the first repeatable:
 *       <ul>
 *         <li>{@code --package=<java package>} puts everything generated in one package;</li>
 *         <li>{@code --implements=<message pattern>=<interface>} adds an interface to matching records;</li>
 *         <li>{@code --annotate=<message pattern>=<annotation>} adds a marker annotation to matching records;</li>
 *         <li>{@code --convert=<message or field>=<java type>:<converter>} declares fields of that message type, or
 *             that one field, as a Java type of the application's own, mapped by a {@code ProtoConverter}.</li>
 *       </ul>
 *       A message pattern is a full name ({@code example.orders.Order}), a prefix ending in {@code .*}, or
 *       {@code *}; a conversion names one message or field by its full name
 *       ({@code example.orders.Order.created_millis});</li>
 *   <li>with no arguments, as a protoc plugin: a {@code CodeGeneratorRequest} on stdin, a
 *       {@code CodeGeneratorResponse} on stdout, so {@code protoc --java-records_out=...} works directly.</li>
 * </ul>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        List<String> rest = new java.util.ArrayList<>(List.of(args));
        String packageOverride = null;
        List<Customizations.Rule> interfaces = new java.util.ArrayList<>();
        List<Customizations.Rule> annotations = new java.util.ArrayList<>();
        List<Customizations.Conversion> conversions = new java.util.ArrayList<>();
        for (java.util.Iterator<String> i = rest.iterator(); i.hasNext(); ) {
            String arg = i.next();
            if (arg.startsWith("--package=")) {
                packageOverride = arg.substring("--package=".length());
            } else if (arg.startsWith("--implements=")) {
                interfaces.add(Customizations.Rule.parse("--implements", arg.substring("--implements=".length())));
            } else if (arg.startsWith("--annotate=")) {
                annotations.add(Customizations.Rule.parse("--annotate", arg.substring("--annotate=".length())));
            } else if (arg.startsWith("--convert=")) {
                conversions.add(Customizations.Conversion.parse(arg.substring("--convert=".length())));
            } else {
                continue;
            }
            i.remove();
        }
        if (rest.isEmpty()) {
            plugin();
        } else if (rest.size() >= 2) {
            cli(
                Path.of(rest.get(0)), Path.of(rest.get(1)), rest.subList(2, rest.size()), packageOverride,
                new Customizations(interfaces, annotations, conversions)
            );
        } else {
            System.err.println("usage: [--package=<java package>] [--implements=<message pattern>=<interface>]..."
                + " [--annotate=<message pattern>=<annotation>]..."
                + " [--convert=<message or field>=<java type>:<converter>]..."
                + " <descriptor-set.pb> <output-dir> [proto-file...]");
            System.exit(2);
        }
    }

    private static void cli(
        Path descriptorSet, Path outputDirectory, List<String> filesToGenerate, String packageOverride,
        Customizations customizations
    ) throws Exception {
        FileDescriptorSet set = FileDescriptorSet.parseFrom(Files.readAllBytes(descriptorSet));
        Map<String, String> sources = Generator.generate(set, filesToGenerate, packageOverride, customizations);
        Set<Path> written = new HashSet<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = outputDirectory.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            write(file, source.getValue());
            written.add(file.toAbsolutePath().normalize());
        }
        int removed = prune(outputDirectory, written);
        System.out.println("generated " + sources.size() + " files in " + outputDirectory
            + (removed == 0 ? "" : " (" + removed + " stale removed)"));
    }

    /**
     * Removes sources left over from an earlier run, so renaming or deleting a message does not leave a stale
     * record behind to be compiled. The output directory belongs to the generator; only {@code .java} files in it
     * are touched.
     */
    private static int prune(Path outputDirectory, Set<Path> written) throws IOException {
        if (!Files.isDirectory(outputDirectory)) return 0;
        List<Path> stale;
        try (Stream<Path> files = Files.walk(outputDirectory)) {
            stale = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !written.contains(path.toAbsolutePath().normalize()))
                .toList();
        }
        for (Path file : stale) Files.delete(file);
        try (Stream<Path> directories = Files.walk(outputDirectory)) {
            for (Path directory : directories.sorted(Comparator.reverseOrder()).toList()) {
                if (!Files.isDirectory(directory) || directory.equals(outputDirectory)) continue;
                try (Stream<Path> entries = Files.list(directory)) {
                    if (entries.findAny().isEmpty()) Files.delete(directory);
                }
            }
        }
        return stale.size();
    }

    /** Writes only when the content changed, so unchanged sources keep their timestamps and builds stay up to date. */
    private static void write(Path file, String source) throws IOException {
        if (Files.exists(file) && Files.readString(file).equals(source)) return;
        Files.writeString(file, source);
    }

    private static void plugin() throws Exception {
        CodeGeneratorRequest request = CodeGeneratorRequest.parseFrom(System.in.readAllBytes());
        CodeGeneratorResponse.Builder response = CodeGeneratorResponse.newBuilder()
            .setSupportedFeatures(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.getNumber());
        try {
            FileDescriptorSet set = FileDescriptorSet.newBuilder().addAllFile(request.getProtoFileList()).build();
            Map<String, String> sources = Generator.generate(set, request.getFileToGenerateList());
            sources.forEach((path, content) -> response.addFileBuilder().setName(path).setContent(content));
        } catch (Exception e) {
            response.setError(e.toString());
        }
        System.out.write(response.build().toByteArray());
        System.out.flush();
    }
}
