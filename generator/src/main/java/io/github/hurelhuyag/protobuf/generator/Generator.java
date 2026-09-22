package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@code FileDescriptorSet} into Java sources: one record per message (nested messages, enums and oneof
 * interfaces nested inside it), one enum per protobuf enum, and one codec per message.
 */
public final class Generator {

    static final String NAME = "io.github.hurelhuyag.protobuf.generator.Generator";

    /** Files whose types map to JDK types instead of generated records. */
    private static final String WELL_KNOWN_PREFIX = "google/protobuf/";

    private Generator() {
    }

    /**
     * @param set            descriptor set, with imports included
     * @param filesToGenerate proto file names to generate for; empty means every file that is not a well-known one
     * @return generated sources, keyed by their path relative to a source root
     */
    public static Map<String, String> generate(FileDescriptorSet set, Collection<String> filesToGenerate)
        throws DescriptorValidationException {
        return generate(set, filesToGenerate, null);
    }

    /**
     * @param packageOverride Java package for everything generated, replacing each file's {@code java_package};
     *                        null to use the schema's own
     */
    public static Map<String, String> generate(
        FileDescriptorSet set, Collection<String> filesToGenerate, String packageOverride
    ) throws DescriptorValidationException {
        Map<String, FileDescriptorProto> protos = new LinkedHashMap<>();
        for (FileDescriptorProto proto : set.getFileList()) {
            protos.putIfAbsent(proto.getName(), packageOverride == null ? proto : withPackage(proto, packageOverride));
        }
        Map<String, FileDescriptor> built = new HashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        for (String name : protos.keySet()) {
            boolean wanted = filesToGenerate.isEmpty() ? !name.startsWith(WELL_KNOWN_PREFIX)
                : filesToGenerate.contains(name);
            FileDescriptor file = build(name, protos, built, new ArrayList<>());
            if (wanted) generate(file, sources);
        }
        return sources;
    }

    private static FileDescriptorProto withPackage(FileDescriptorProto proto, String javaPackage) {
        if (proto.getName().startsWith(WELL_KNOWN_PREFIX)) return proto;
        return proto.toBuilder().setOptions(proto.getOptions().toBuilder().setJavaPackage(javaPackage)).build();
    }

    private static void generate(FileDescriptor file, Map<String, String> sources) {
        String packageName = Names.javaPackage(file);
        String directory = packageName.replace('.', '/');
        for (EnumDescriptor enumType : file.getEnumTypes()) {
            put(sources, path(directory, enumType.getName()), EnumWriter.write(enumType), packageName);
        }
        for (Descriptor message : file.getMessageTypes()) {
            put(sources, path(directory, message.getName()), RecordWriter.write(message), packageName);
            codecs(message, directory, packageName, sources);
        }
    }

    private static void codecs(
        Descriptor message, String directory, String packageName, Map<String, String> sources
    ) {
        String path = path(directory, simpleName(Names.codecName(message)));
        put(sources, path, CodecWriter.write(message), packageName);
        for (Descriptor nested : message.getNestedTypes()) {
            if (!nested.getOptions().getMapEntry()) codecs(nested, directory, packageName, sources);
        }
    }

    private static void put(Map<String, String> sources, String path, String source, String packageName) {
        sources.put(path, Sources.simplify(source, packageName));
    }

    private static String path(String directory, String simpleName) {
        return directory.isEmpty() ? simpleName + ".java" : directory + "/" + simpleName + ".java";
    }

    private static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    private static FileDescriptor build(
        String name, Map<String, FileDescriptorProto> protos, Map<String, FileDescriptor> built, List<String> stack
    ) throws DescriptorValidationException {
        FileDescriptor existing = built.get(name);
        if (existing != null) return existing;
        FileDescriptorProto proto = protos.get(name);
        if (proto == null) {
            throw new IllegalArgumentException("descriptor set does not contain '" + name
                + "'; run protoc with --include_imports");
        }
        if (stack.contains(name)) throw new IllegalArgumentException("circular import involving " + name);
        stack.add(name);
        FileDescriptor[] dependencies = new FileDescriptor[proto.getDependencyCount()];
        for (int i = 0; i < dependencies.length; i++) {
            dependencies[i] = build(proto.getDependency(i), protos, built, stack);
        }
        stack.remove(stack.size() - 1);
        FileDescriptor file = FileDescriptor.buildFrom(proto, dependencies);
        built.put(name, file);
        return file;
    }
}
