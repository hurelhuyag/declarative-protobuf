package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
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

    private Generator() {
    }

    /**
     * @param set            descriptor set, with imports included
     * @param filesToGenerate proto file names to generate for; empty means every file except protobuf's own
     * @return generated sources, keyed by their path relative to a source root
     */
    public static Map<String, String> generate(FileDescriptorSet set, Collection<String> filesToGenerate)
        throws DescriptorValidationException {
        return generate(set, filesToGenerate, null, Customizations.NONE);
    }

    /**
     * @param packageOverride Java package for everything generated, replacing each file's {@code java_package};
     *                        null to use the schema's own
     * @param customizations  interfaces, annotations and conversions the build asks for; every interface and
     *                        annotation rule must match a generated record, every conversion a message or field
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
                ? !name.startsWith(WELL_KNOWN_PREFIX)
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
        // a conversion may name a message of an imported file, whose fields elsewhere it then converts
        Set<String> convertible = new HashSet<>();
        for (FileDescriptor file : built.values()) {
            for (Descriptor message : file.getMessageTypes()) convertibleNames(message, customizations, convertible);
        }
        Set<String> converted = new HashSet<>();
        for (Customizations.Conversion conversion : customizations.conversions()) {
            if (!convertible.contains(conversion.target())) {
                throw new IllegalArgumentException("'" + conversion.target() + "' (for " + conversion.converter()
                    + ") names no message or field that can be converted");
            }
            if (!converted.add(conversion.target())) {
                throw new IllegalArgumentException("'" + conversion.target() + "' is converted more than once");
            }
        }
        return sources;
    }

    /**
     * Full names a conversion can target: a message, nested ones included, and each of its fields but a map. A map
     * entry is no target, and neither is a map field: a map's values are converted through their message type.
     * Fails when a converted message's prefixed record name is already taken by a type declared next to it.
     */
    private static void convertibleNames(Descriptor message, Customizations customizations, Set<String> names) {
        names.add(message.getFullName());
        String recordName = Names.recordName(message, customizations);
        List<String> siblings = new ArrayList<>();
        Descriptor parent = message.getContainingType();
        for (Descriptor sibling : parent == null ? message.getFile().getMessageTypes() : parent.getNestedTypes()) {
            if (!sibling.getOptions().getMapEntry()) siblings.add(sibling.getName());
        }
        for (EnumDescriptor sibling : parent == null ? message.getFile().getEnumTypes() : parent.getEnumTypes()) {
            siblings.add(sibling.getName());
        }
        if (!recordName.equals(message.getName()) && siblings.contains(recordName)) {
            throw new IllegalArgumentException("'" + message.getFullName() + "' is converted, so its record is named "
                + recordName + ", but the schema declares a " + recordName + " next to it");
        }
        for (FieldDescriptor field : message.getFields()) {
            if (!field.isMapField()) names.add(field.getFullName());
        }
        for (Descriptor nested : message.getNestedTypes()) {
            if (!nested.getOptions().getMapEntry()) convertibleNames(nested, customizations, names);
        }
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
                sources, path(directory, Names.recordName(message, customizations)),
                RecordWriter.write(message, customizations), packageName, extra
            );
            codecs(message, customizations, directory, packageName, extra, sources);
        }
    }

    private static void codecs(
        Descriptor message, Customizations customizations, String directory, String packageName,
        Map<String, String> extra, Map<String, String> sources
    ) {
        String path = path(directory, simpleName(Names.codecName(message, customizations)));
        put(sources, path, CodecWriter.write(message, customizations), packageName, extra);
        for (Descriptor nested : message.getNestedTypes()) {
            if (!nested.getOptions().getMapEntry()) {
                codecs(nested, customizations, directory, packageName, extra, sources);
            }
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
        for (Descriptor message : file.getMessageTypes()) declared.add(Names.recordName(message, customizations));
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
        for (Field field : TypeMapper.fields(message, customizations)) {
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
