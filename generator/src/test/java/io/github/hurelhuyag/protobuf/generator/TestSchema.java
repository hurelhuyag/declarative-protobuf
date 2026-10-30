package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DurationProto;
import com.google.protobuf.TimestampProto;
import com.google.protobuf.WrappersProto;

/**
 * The equivalent of this {@code demo.proto}, built without protoc:
 * <pre>
 * syntax = "proto3";
 * package demo;
 * enum Color { COLOR_UNSPECIFIED = 0; RED = 1; GREEN = 2; }
 * message Point { int32 x = 1; int32 y = 2; }
 * message Tree { string name = 1; repeated Tree children = 2; }
 * message Segment { Point start = 1; Point end = 2; }
 * message Everything {
 *   // every scalar type, then optional, enum, message, repeated, map, oneof, well-known types and a nested type;
 *   // the message fields are all optional, so that a message of defaults is empty
 *   ...
 *   oneof shape { string label = 27; int32 radius = 28; Point center = 29; }
 *   message Nested { enum Kind { KIND_UNSPECIFIED = 0; A = 1; } int32 n = 1; Kind kind = 2; }
 * }
 * </pre>
 */
final class TestSchema {

    static final FileDescriptorProto FILE = FileDescriptorProto.newBuilder()
        .setName("demo.proto")
        .setPackage("demo")
        .setSyntax("proto3")
        .setOptions(FileOptions.newBuilder().setJavaPackage("demo"))
        .addDependency("google/protobuf/timestamp.proto")
        .addDependency("google/protobuf/duration.proto")
        .addDependency("google/protobuf/wrappers.proto")
        .addEnumType(EnumDescriptorProto.newBuilder().setName("Color")
            .addValue(value("COLOR_UNSPECIFIED", 0))
            .addValue(value("RED", 1))
            .addValue(value("GREEN", 2)))
        .addMessageType(DescriptorProto.newBuilder().setName("Uuid")
            .setOptions(MessageOptions.newBuilder().setUnknownFields(
                converted("java.util.UUID", "demo.UuidConverter")))
            .addField(field("msb", 1, Type.TYPE_FIXED64))
            .addField(field("lsb", 2, Type.TYPE_FIXED64)))
        .addMessageType(DescriptorProto.newBuilder().setName("Point")
            .addField(field("x", 1, Type.TYPE_INT32))
            .addField(field("y", 2, Type.TYPE_INT32)))
        .addMessageType(DescriptorProto.newBuilder().setName("Tree")
            .addField(field("name", 1, Type.TYPE_STRING))
            .addField(message("children", 2, ".demo.Tree").setLabel(Label.LABEL_REPEATED)))
        .addMessageType(DescriptorProto.newBuilder().setName("Segment")
            .addField(message("start", 1, ".demo.Point"))
            .addField(message("end", 2, ".demo.Point")))
        .addMessageType(DescriptorProto.newBuilder().setName("Everything")
            .addField(field("double_value", 1, Type.TYPE_DOUBLE))
            .addField(field("float_value", 2, Type.TYPE_FLOAT))
            .addField(field("int32_value", 3, Type.TYPE_INT32))
            .addField(field("int64_value", 4, Type.TYPE_INT64))
            .addField(field("uint32_value", 5, Type.TYPE_UINT32))
            .addField(field("uint64_value", 6, Type.TYPE_UINT64))
            .addField(field("sint32_value", 7, Type.TYPE_SINT32))
            .addField(field("sint64_value", 8, Type.TYPE_SINT64))
            .addField(field("fixed32_value", 9, Type.TYPE_FIXED32))
            .addField(field("fixed64_value", 10, Type.TYPE_FIXED64))
            .addField(field("sfixed32_value", 11, Type.TYPE_SFIXED32))
            .addField(field("sfixed64_value", 12, Type.TYPE_SFIXED64))
            .addField(field("bool_value", 13, Type.TYPE_BOOL))
            .addField(field("string_value", 14, Type.TYPE_STRING))
            .addField(field("bytes_value", 15, Type.TYPE_BYTES))
            .addField(field("opt_int32", 16, Type.TYPE_INT32).setProto3Optional(true).setOneofIndex(1))
            .addField(field("opt_string", 17, Type.TYPE_STRING).setProto3Optional(true).setOneofIndex(2))
            .addField(message("color", 18, ".demo.Color").setType(Type.TYPE_ENUM))
            .addField(message("point", 19, ".demo.Point").setProto3Optional(true).setOneofIndex(3))
            .addField(message("tree", 20, ".demo.Tree").setProto3Optional(true).setOneofIndex(4))
            .addField(field("ints", 21, Type.TYPE_INT32).setLabel(Label.LABEL_REPEATED))
            .addField(field("strings", 22, Type.TYPE_STRING).setLabel(Label.LABEL_REPEATED))
            .addField(message("points", 23, ".demo.Point").setLabel(Label.LABEL_REPEATED))
            .addField(message("colors", 24, ".demo.Color").setType(Type.TYPE_ENUM).setLabel(Label.LABEL_REPEATED))
            .addField(message("counts", 25, ".demo.Everything.CountsEntry").setLabel(Label.LABEL_REPEATED))
            .addField(message("points_by_id", 26, ".demo.Everything.PointsByIdEntry").setLabel(Label.LABEL_REPEATED))
            .addField(field("label", 27, Type.TYPE_STRING).setOneofIndex(0))
            .addField(field("radius", 28, Type.TYPE_INT32).setOneofIndex(0))
            .addField(message("center", 29, ".demo.Point").setOneofIndex(0))
            .addField(message("created_at", 30, ".google.protobuf.Timestamp").setProto3Optional(true).setOneofIndex(5))
            .addField(message("ttl", 31, ".google.protobuf.Duration").setProto3Optional(true).setOneofIndex(6))
            .addField(message("maybe", 32, ".google.protobuf.Int32Value").setProto3Optional(true).setOneofIndex(7))
            .addField(message("nested", 33, ".demo.Everything.Nested").setProto3Optional(true).setOneofIndex(8))
            .addField(message("request_id", 34, ".demo.Uuid").setProto3Optional(true).setOneofIndex(9))
            .addField(message("related_ids", 35, ".demo.Uuid").setLabel(Label.LABEL_REPEATED))
            .addField(field("created_millis", 36, Type.TYPE_INT64)
                .setOptions(FieldOptions.newBuilder().setUnknownFields(
                    converted("java.time.Instant", "demo.MillisConverter"))))
            .addField(message("owners", 37, ".demo.Everything.OwnersEntry").setLabel(Label.LABEL_REPEATED))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("shape"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_opt_int32"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_opt_string"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_point"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_tree"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_created_at"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_ttl"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_maybe"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_nested"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_request_id"))
            .addNestedType(mapEntry("CountsEntry", field("key", 1, Type.TYPE_STRING),
                field("value", 2, Type.TYPE_INT32)))
            .addNestedType(mapEntry("PointsByIdEntry", field("key", 1, Type.TYPE_INT64),
                message("value", 2, ".demo.Point")))
            .addNestedType(mapEntry("OwnersEntry", field("key", 1, Type.TYPE_STRING),
                message("value", 2, ".demo.Uuid")))
            .addNestedType(DescriptorProto.newBuilder().setName("Nested")
                .addEnumType(EnumDescriptorProto.newBuilder().setName("Kind")
                    .addValue(value("KIND_UNSPECIFIED", 0))
                    .addValue(value("A", 1)))
                .addField(field("n", 1, Type.TYPE_INT32))
                .addField(message("kind", 2, ".demo.Everything.Nested.Kind").setType(Type.TYPE_ENUM))))
        .build();

    /** The custom option exactly as protoc serialises it: a JavaType message in extension field 50001. */
    private static UnknownFieldSet converted(String javaType, String converter) {
        ByteString value = UnknownFieldSet.newBuilder()
            .addField(1, UnknownFieldSet.Field.newBuilder()
                .addLengthDelimited(ByteString.copyFromUtf8(javaType)).build())
            .addField(2, UnknownFieldSet.Field.newBuilder()
                .addLengthDelimited(ByteString.copyFromUtf8(converter)).build())
            .build()
            .toByteString();
        return UnknownFieldSet.newBuilder()
            .addField(50001, UnknownFieldSet.Field.newBuilder().addLengthDelimited(value).build())
            .build();
    }

    private static EnumValueDescriptorProto.Builder value(String name, int number) {
        return EnumValueDescriptorProto.newBuilder().setName(name).setNumber(number);
    }

    private static FieldDescriptorProto.Builder field(String name, int number, Type type) {
        return FieldDescriptorProto.newBuilder().setName(name).setNumber(number).setType(type)
            .setLabel(Label.LABEL_OPTIONAL).setJsonName(jsonName(name));
    }

    private static FieldDescriptorProto.Builder message(String name, int number, String typeName) {
        return field(name, number, Type.TYPE_MESSAGE).setTypeName(typeName);
    }

    private static DescriptorProto.Builder mapEntry(
        String name, FieldDescriptorProto.Builder key, FieldDescriptorProto.Builder value
    ) {
        return DescriptorProto.newBuilder().setName(name).addField(key).addField(value)
            .setOptions(MessageOptions.newBuilder().setMapEntry(true));
    }

    /** protoc fills json_name in; building descriptors by hand means doing it here. */
    private static String jsonName(String protoName) {
        return Names.lowerCamel(protoName);
    }

    static FileDescriptorSet set() {
        return FileDescriptorSet.newBuilder()
            .addFile(TimestampProto.getDescriptor().toProto())
            .addFile(DurationProto.getDescriptor().toProto())
            .addFile(WrappersProto.getDescriptor().toProto())
            .addFile(FILE)
            .build();
    }

    static final FileDescriptor DESCRIPTOR;

    static {
        try {
            DESCRIPTOR = FileDescriptor.buildFrom(FILE, new FileDescriptor[]{
                TimestampProto.getDescriptor(), DurationProto.getDescriptor(), WrappersProto.getDescriptor()
            });
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Descriptor message(String name) {
        return DESCRIPTOR.findMessageTypeByName(name);
    }

    private TestSchema() {
    }
}
