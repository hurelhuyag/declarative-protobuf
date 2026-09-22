package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.AnyProto;
import com.google.protobuf.ApiProto;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DurationProto;
import com.google.protobuf.EmptyProto;
import com.google.protobuf.FieldMaskProto;
import com.google.protobuf.SourceContextProto;
import com.google.protobuf.StructProto;
import com.google.protobuf.TimestampProto;
import com.google.protobuf.TypeProto;
import com.google.protobuf.WrappersProto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All messages and enums from one or more {@code FileDescriptorSet} files (as produced by
 * {@code protoc --descriptor_set_out}), indexed by fully qualified and by simple name.
 */
final class Schema {

    /** Bundled descriptors for {@code google/protobuf/*.proto} so imports of them need not be in the set. */
    private static final Map<String, FileDescriptor> BUILT_IN = Map.ofEntries(
        Map.entry("google/protobuf/any.proto", AnyProto.getDescriptor()),
        Map.entry("google/protobuf/api.proto", ApiProto.getDescriptor()),
        Map.entry("google/protobuf/descriptor.proto", DescriptorProtos.getDescriptor()),
        Map.entry("google/protobuf/duration.proto", DurationProto.getDescriptor()),
        Map.entry("google/protobuf/empty.proto", EmptyProto.getDescriptor()),
        Map.entry("google/protobuf/field_mask.proto", FieldMaskProto.getDescriptor()),
        Map.entry("google/protobuf/source_context.proto", SourceContextProto.getDescriptor()),
        Map.entry("google/protobuf/struct.proto", StructProto.getDescriptor()),
        Map.entry("google/protobuf/timestamp.proto", TimestampProto.getDescriptor()),
        Map.entry("google/protobuf/type.proto", TypeProto.getDescriptor()),
        Map.entry("google/protobuf/wrappers.proto", WrappersProto.getDescriptor())
    );

    private final Map<String, Descriptor> messagesByFullName = new LinkedHashMap<>();
    private final Map<String, List<Descriptor>> messagesBySimpleName = new HashMap<>();
    private final Map<String, Descriptor> messagesByJavaName = new HashMap<>();
    private final Map<String, EnumDescriptor> enumsByFullName = new LinkedHashMap<>();
    private final Map<String, List<EnumDescriptor>> enumsBySimpleName = new HashMap<>();
    private final Map<String, EnumDescriptor> enumsByJavaName = new HashMap<>();

    static Schema load(List<Path> descriptorSets) throws IOException, DescriptorValidationException {
        Map<String, FileDescriptorProto> protos = new LinkedHashMap<>();
        for (Path path : descriptorSets) {
            for (FileDescriptorProto proto : FileDescriptorSet.parseFrom(Files.readAllBytes(path)).getFileList()) {
                protos.putIfAbsent(proto.getName(), proto);
            }
        }
        Map<String, FileDescriptor> built = new HashMap<>(BUILT_IN);
        Schema schema = new Schema();
        for (String name : protos.keySet()) {
            schema.index(build(name, protos, built, new ArrayList<>()));
        }
        return schema;
    }

    private static FileDescriptor build(
        String name, Map<String, FileDescriptorProto> protos, Map<String, FileDescriptor> built, List<String> stack
    ) throws DescriptorValidationException {
        FileDescriptor existing = built.get(name);
        if (existing != null) return existing;
        FileDescriptorProto proto = protos.get(name);
        if (proto == null) {
            throw new IllegalArgumentException(
                "descriptor set does not contain '" + name + "' (imported by " + stack.get(stack.size() - 1)
                    + "); run protoc with --include_imports"
            );
        }
        if (stack.contains(name)) throw new IllegalArgumentException("circular import involving " + name);
        stack.add(name);
        FileDescriptor[] deps = new FileDescriptor[proto.getDependencyCount()];
        for (int i = 0; i < deps.length; i++) {
            deps[i] = build(proto.getDependency(i), protos, built, stack);
        }
        stack.remove(stack.size() - 1);
        FileDescriptor file = FileDescriptor.buildFrom(proto, deps);
        built.put(name, file);
        return file;
    }

    private void index(FileDescriptor file) {
        for (Descriptor message : file.getMessageTypes()) index(message);
        for (EnumDescriptor enumType : file.getEnumTypes()) index(enumType);
    }

    private void index(Descriptor message) {
        if (message.getOptions().getMapEntry()) return;
        messagesByFullName.put(message.getFullName(), message);
        messagesBySimpleName.computeIfAbsent(message.getName(), k -> new ArrayList<>()).add(message);
        messagesByJavaName.put(javaName(message.getFile(), message.getFullName()), message);
        for (Descriptor nested : message.getNestedTypes()) index(nested);
        for (EnumDescriptor enumType : message.getEnumTypes()) index(enumType);
    }

    private void index(EnumDescriptor enumType) {
        enumsByFullName.put(enumType.getFullName(), enumType);
        enumsBySimpleName.computeIfAbsent(enumType.getName(), k -> new ArrayList<>()).add(enumType);
        enumsByJavaName.put(javaName(enumType.getFile(), enumType.getFullName()), enumType);
    }

    /**
     * The qualified Java name a type maps to: the file's {@code java_package} (or its proto package when unset)
     * plus the type's path within the file, e.g. {@code com.acme.orders.Outer.Inner} for {@code Outer.Inner} in
     * a file with {@code package acme.orders; option java_package = "com.acme.orders";}.
     */
    private static String javaName(FileDescriptor file, String fullName) {
        String protoPackage = file.getPackage();
        String path = protoPackage.isEmpty() ? fullName : fullName.substring(protoPackage.length() + 1);
        String javaPackage = file.getOptions().hasJavaPackage() ? file.getOptions().getJavaPackage() : protoPackage;
        return javaPackage.isEmpty() ? path : javaPackage + "." + path;
    }

    Descriptor message(String fullName) {
        return messagesByFullName.get(fullName);
    }

    List<Descriptor> messagesNamed(String simpleName) {
        return messagesBySimpleName.getOrDefault(simpleName, List.of());
    }

    /** The message whose Java name (see {@link #javaName}) is the given qualified class name, or null. */
    Descriptor messageForJavaName(String qualifiedName) {
        return messagesByJavaName.get(qualifiedName);
    }

    EnumDescriptor enumType(String fullName) {
        return enumsByFullName.get(fullName);
    }

    List<EnumDescriptor> enumsNamed(String simpleName) {
        return enumsBySimpleName.getOrDefault(simpleName, List.of());
    }

    EnumDescriptor enumForJavaName(String qualifiedName) {
        return enumsByJavaName.get(qualifiedName);
    }
}
