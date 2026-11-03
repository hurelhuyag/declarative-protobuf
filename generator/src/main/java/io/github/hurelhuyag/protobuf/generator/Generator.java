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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Turns a {@code FileDescriptorSet} into Java sources: one record per message (nested messages, enums and oneof
 * interfaces nested inside it), one enum per protobuf enum, and one codec per message.
 */
public final class Generator {

    static final String NAME = "io.github.hurelhuyag.protobuf.generator.Generator";

    /** Files whose types map to JDK types instead of generated records. */
    private static final String WELL_KNOWN_PREFIX = "google/protobuf/";

    /** Our own options file: imported to be read by the generator, never generated from. */
    private static final String OPTIONS_FILE = "declarative/options.proto";

    private Generator() {
    }

    /**
     * @param set            descriptor set, with imports included
     * @param filesToGenerate proto file names to generate for; empty means every file except protobuf's own and
     *                        {@value #OPTIONS_FILE}
     * @return generated sources, keyed by their path relative to a source root
     */
    public static Map<String, String> generate(FileDescriptorSet set, Collection<String> filesToGenerate)
        throws DescriptorValidationException {
        return generate(set, filesToGenerate, null, Customizations.NONE);
    }

    /**
     * @param packageOverride Java package for everything generated, replacing each file's {@code java_package};
     *                        null to use the schema's own
     * @param customizations  interfaces and annotations the build adds to generated records; every rule must match
     *                        at least one of them
     */
    public static Map<String, String> generate(
        FileDescriptorSet set, Collection<String> filesToGenerate, String packageOverride,
        Customizations customizations
    ) throws DescriptorValidationException {
        Map<String, FileDescriptorProto> protos = new LinkedHashMap<>();
        for (FileDescriptorProto proto : set.getFileList()) {
            protos.putIfAbsent(proto.getName(), packageOverride == null ? proto : withPackage(proto, packageOverride));
        }
        Map<String, FileDescriptor> built = new HashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        List<String> records = new ArrayList<>();
        for (String name : protos.keySet()) {
            boolean wanted = filesToGenerate.isEmpty()
                ? !name.startsWith(WELL_KNOWN_PREFIX) && !name.equals(OPTIONS_FILE)
                : filesToGenerate.contains(name);
            FileDescriptor file = build(name, protos, built, new ArrayList<>());
            if (!wanted) continue;
            generate(file, customizations, sources);
            for (Descriptor message : file.getMessageTypes()) recordNames(message, records);
        }
        // a rule that matches nothing is a typo or a stale name; failing the build says so where silence would not
        List<Customizations.Rule> rules = new ArrayList<>(customizations.interfaces());
        rules.addAll(customizations.annotations());
        for (Customizations.Rule rule : rules) {
            if (records.stream().noneMatch(rule::matches)) {
                throw new IllegalArgumentException("'" + rule.pattern() + "' (for " + rule.type()
                    + ") matches no generated message");
            }
        }
        return sources;
    }

    /** Full names of the records generated for a message: itself and its nested messages, but no map entries. */
    private static void recordNames(Descriptor message, List<String> names) {
        names.add(message.getFullName());
        for (Descriptor nested : message.getNestedTypes()) {
            if (!nested.getOptions().getMapEntry()) recordNames(nested, names);
        }
    }

    private static FileDescriptorProto withPackage(FileDescriptorProto proto, String javaPackage) {
        if (proto.getName().startsWith(WELL_KNOWN_PREFIX)) return proto;
        return proto.toBuilder().setOptions(proto.getOptions().toBuilder().setJavaPackage(javaPackage)).build();
    }

    private static void generate(FileDescriptor file, Customizations customizations, Map<String, String> sources) {
        String packageName = Names.javaPackage(file);
        String directory = packageName.replace('.', '/');
        Map<String, String> extra = importableTypes(file, customizations);
        for (EnumDescriptor enumType : file.getEnumTypes()) {
            put(sources, path(directory, enumType.getName()), EnumWriter.write(enumType), packageName, extra);
        }
        for (Descriptor message : file.getMessageTypes()) {
            put(
                sources, path(directory, message.getName()), RecordWriter.write(message, customizations), packageName,
                extra
            );
            codecs(message, directory, packageName, extra, sources);
        }
    }

    private static void codecs(
        Descriptor message, String directory, String packageName, Map<String, String> extra,
        Map<String, String> sources
    ) {
        String path = path(directory, simpleName(Names.codecName(message)));
        put(sources, path, CodecWriter.write(message), packageName, extra);
        for (Descriptor nested : message.getNestedTypes()) {
            if (!nested.getOptions().getMapEntry()) codecs(nested, directory, packageName, extra, sources);
        }
    }

    private static void put(
        Map<String, String> sources, String path, String source, String packageName, Map<String, String> extra
    ) {
        sources.put(path, Sources.simplify(source, packageName, extra));
    }

    /**
     * Types a converter or a customization introduced, ready to be imported: only those whose simple name cannot be
     * confused with a type this file declares or with another introduced type.
     */
    private static Map<String, String> importableTypes(FileDescriptor file, Customizations customizations) {
        Set<String> declared = new HashSet<>();
        for (Descriptor message : file.getMessageTypes()) declared.add(message.getName());
        for (EnumDescriptor enumType : file.getEnumTypes()) declared.add(enumType.getName());
        Set<String> introduced = new TreeSet<>();
        for (Descriptor message : file.getMessageTypes()) introducedTypes(message, customizations, introduced);
        Map<String, Long> bySimpleName = introduced.stream()
            .collect(Collectors.groupingBy(Generator::simpleName, Collectors.counting()));
        Map<String, String> importable = new LinkedHashMap<>();
        for (String type : introduced) {
            String simple = simpleName(type);
            // a type of the file's own package needs no import: its prefix is dropped like any other's from there
            boolean ownPackage = type.equals(Names.javaPackage(file) + "." + simple);
            if (!declared.contains(simple) && bySimpleName.get(simple) == 1 && type.contains(".") && !ownPackage) {
                importable.put(type, simple);
            }
        }
        return importable;
    }

    private static void introducedTypes(Descriptor message, Customizations customizations, Set<String> types) {
        // a generic interface is imported by its raw name; its type arguments are left as written
        for (String type : customizations.interfacesFor(message)) types.add(type.split("<", 2)[0]);
        types.addAll(customizations.annotationsFor(message));
        for (Field field : TypeMapper.fields(message)) {
            if (field instanceof Field.Singular s) add(types, s.value());
            else if (field instanceof Field.Repeated r) add(types, r.element());
            else if (field instanceof Field.MapField m) {
                add(types, m.key());
                add(types, m.value());
            } else if (field instanceof Field.Oneof o) {
                for (Field.Oneof.Case member : o.cases()) add(types, member.value());
            }
        }
        for (Descriptor nested : message.getNestedTypes()) {
            if (!nested.getOptions().getMapEntry()) introducedTypes(nested, customizations, types);
        }
    }

    private static void add(Set<String> types, Value value) {
        if (value.converterClass() != null) types.add(value.type());
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
