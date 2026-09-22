package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Two ways to run the generator:
 * <ul>
 *   <li>{@code java -jar generator.jar <descriptor-set.pb> <output-dir> [proto-file...]} — reads a descriptor set
 *       written by {@code protoc --descriptor_set_out=... --include_imports} and writes sources to a directory;</li>
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
        for (java.util.Iterator<String> i = rest.iterator(); i.hasNext(); ) {
            String arg = i.next();
            if (arg.startsWith("--package=")) {
                packageOverride = arg.substring("--package=".length());
                i.remove();
            }
        }
        if (rest.isEmpty()) {
            plugin();
        } else if (rest.size() >= 2) {
            cli(Path.of(rest.get(0)), Path.of(rest.get(1)), rest.subList(2, rest.size()), packageOverride);
        } else {
            System.err.println("usage: [--package=<java package>] <descriptor-set.pb> <output-dir> [proto-file...]");
            System.exit(2);
        }
    }

    private static void cli(
        Path descriptorSet, Path outputDirectory, List<String> filesToGenerate, String packageOverride
    ) throws Exception {
        FileDescriptorSet set = FileDescriptorSet.parseFrom(Files.readAllBytes(descriptorSet));
        Map<String, String> sources = Generator.generate(set, filesToGenerate, packageOverride);
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = outputDirectory.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            write(file, source.getValue());
        }
        System.out.println("generated " + sources.size() + " files in " + outputDirectory);
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
