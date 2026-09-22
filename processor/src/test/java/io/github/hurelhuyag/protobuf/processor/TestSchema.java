package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DurationProto;
import com.google.protobuf.TimestampProto;
import com.google.protobuf.WrappersProto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The equivalent of this {@code test.proto}, built without protoc:
 * <pre>
 * syntax = "proto3";
 * package test;
 * option java_package = "fixture";
 * enum Status { STATUS_UNSPECIFIED = 0; OPEN = 1; CLOSED = 2; }
 * enum Status { ...; reserved 10 to 12; }
 * message Line { string sku = 1; int32 qty = 2; double price = 3; reserved 4 to 5; reserved "legacy"; }
 * message Order {
 *   int64 id = 1; string customer = 2; repeated Line lines = 3; repeated int32 tags = 4;
 *   map&lt;string, int32&gt; attributes = 5; Status status = 6; optional int32 discount = 7;
 *   google.protobuf.Timestamp created_at = 8; oneof payment { string card = 9; int64 cash_cents = 10; }
 *   bytes blob = 11; Line primary = 12; repeated Status history = 13; sint32 delta = 14; fixed64 hash = 15;
 *   bool flag = 16; uint32 unsigned = 17; float ratio = 18; map&lt;int64, Line&gt; lines_by_id = 19;
 *   google.protobuf.Duration ttl = 20; google.protobuf.Int32Value maybe = 21; repeated string notes = 22;
 *   Status status_num = 23; Tree tree = 24;
 * }
 * message Tree { int32 value = 1; repeated Tree children = 2; }
 * message Outer { message Inner { int32 n = 1; } Inner inner = 1; }
 * </pre>
 * plus a second file {@code other.proto} ({@code package other; option java_package = "fixture.other";}) holding
 * another message called {@code Line}, so that simple-name matching alone would be ambiguous.
 */
final class TestSchema {

    static final FileDescriptorProto FILE = FileDescriptorProto.newBuilder()
        .setName("test.proto")
        .setPackage("test")
        .setSyntax("proto3")
        .setOptions(FileOptions.newBuilder().setJavaPackage("fixture"))
        .addDependency("google/protobuf/timestamp.proto")
        .addDependency("google/protobuf/duration.proto")
        .addDependency("google/protobuf/wrappers.proto")
        .addEnumType(EnumDescriptorProto.newBuilder().setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("STATUS_UNSPECIFIED").setNumber(0))
            .addValue(EnumValueDescriptorProto.newBuilder().setName("OPEN").setNumber(1))
            .addValue(EnumValueDescriptorProto.newBuilder().setName("CLOSED").setNumber(2))
            .addReservedRange(EnumDescriptorProto.EnumReservedRange.newBuilder().setStart(10).setEnd(12)))
        .addMessageType(DescriptorProto.newBuilder().setName("Line")
            .addField(scalar("sku", 1, Type.TYPE_STRING))
            .addField(scalar("qty", 2, Type.TYPE_INT32))
            .addField(scalar("price", 3, Type.TYPE_DOUBLE))
            .addReservedRange(DescriptorProto.ReservedRange.newBuilder().setStart(4).setEnd(6))
            .addReservedName("legacy"))
        .addMessageType(DescriptorProto.newBuilder().setName("Order")
            .addField(scalar("id", 1, Type.TYPE_INT64))
            .addField(scalar("customer", 2, Type.TYPE_STRING))
            .addField(field("lines", 3, Type.TYPE_MESSAGE, ".test.Line").setLabel(Label.LABEL_REPEATED))
            .addField(scalar("tags", 4, Type.TYPE_INT32).setLabel(Label.LABEL_REPEATED))
            .addField(field("attributes", 5, Type.TYPE_MESSAGE, ".test.Order.AttributesEntry")
                .setLabel(Label.LABEL_REPEATED))
            .addField(field("status", 6, Type.TYPE_ENUM, ".test.Status"))
            .addField(scalar("discount", 7, Type.TYPE_INT32).setProto3Optional(true).setOneofIndex(1))
            .addField(field("created_at", 8, Type.TYPE_MESSAGE, ".google.protobuf.Timestamp"))
            .addField(scalar("card", 9, Type.TYPE_STRING).setOneofIndex(0))
            .addField(scalar("cash_cents", 10, Type.TYPE_INT64).setOneofIndex(0))
            .addField(scalar("blob", 11, Type.TYPE_BYTES))
            .addField(field("primary", 12, Type.TYPE_MESSAGE, ".test.Line"))
            .addField(field("history", 13, Type.TYPE_ENUM, ".test.Status").setLabel(Label.LABEL_REPEATED))
            .addField(scalar("delta", 14, Type.TYPE_SINT32))
            .addField(scalar("hash", 15, Type.TYPE_FIXED64))
            .addField(scalar("flag", 16, Type.TYPE_BOOL))
            .addField(scalar("unsigned", 17, Type.TYPE_UINT32))
            .addField(scalar("ratio", 18, Type.TYPE_FLOAT))
            .addField(field("lines_by_id", 19, Type.TYPE_MESSAGE, ".test.Order.LinesByIdEntry")
                .setLabel(Label.LABEL_REPEATED))
            .addField(field("ttl", 20, Type.TYPE_MESSAGE, ".google.protobuf.Duration"))
            .addField(field("maybe", 21, Type.TYPE_MESSAGE, ".google.protobuf.Int32Value"))
            .addField(scalar("notes", 22, Type.TYPE_STRING).setLabel(Label.LABEL_REPEATED))
            .addField(field("status_num", 23, Type.TYPE_ENUM, ".test.Status"))
            .addField(field("tree", 24, Type.TYPE_MESSAGE, ".test.Tree"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("payment"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_discount"))
            .addNestedType(mapEntry("AttributesEntry", scalar("key", 1, Type.TYPE_STRING),
                scalar("value", 2, Type.TYPE_INT32)))
            .addNestedType(mapEntry("LinesByIdEntry", scalar("key", 1, Type.TYPE_INT64),
                field("value", 2, Type.TYPE_MESSAGE, ".test.Line"))))
        .addMessageType(DescriptorProto.newBuilder().setName("Tree")
            .addField(scalar("value", 1, Type.TYPE_INT32))
            .addField(field("children", 2, Type.TYPE_MESSAGE, ".test.Tree").setLabel(Label.LABEL_REPEATED)))
        .addMessageType(DescriptorProto.newBuilder().setName("Outer")
            .addField(field("inner", 1, Type.TYPE_MESSAGE, ".test.Outer.Inner"))
            .addNestedType(DescriptorProto.newBuilder().setName("Inner").addField(scalar("n", 1, Type.TYPE_INT32))))
        .build();

    static final FileDescriptorProto OTHER = FileDescriptorProto.newBuilder()
        .setName("other.proto")
        .setPackage("other")
        .setSyntax("proto3")
        .setOptions(FileOptions.newBuilder().setJavaPackage("fixture.other"))
        .addMessageType(DescriptorProto.newBuilder().setName("Line").addField(scalar("name", 1, Type.TYPE_STRING)))
        .build();

    private static FieldDescriptorProto.Builder scalar(String name, int number, Type type) {
        return FieldDescriptorProto.newBuilder().setName(name).setNumber(number).setType(type)
            .setLabel(Label.LABEL_OPTIONAL);
    }

    private static FieldDescriptorProto.Builder field(String name, int number, Type type, String typeName) {
        return scalar(name, number, type).setTypeName(typeName);
    }

    private static DescriptorProto.Builder mapEntry(
        String name, FieldDescriptorProto.Builder key, FieldDescriptorProto.Builder value
    ) {
        return DescriptorProto.newBuilder().setName(name).addField(key).addField(value)
            .setOptions(MessageOptions.newBuilder().setMapEntry(true));
    }

    /** Writes a descriptor set without imports, relying on the processor's bundled google/protobuf descriptors. */
    static Path write(Path dir) throws IOException {
        Path file = dir.resolve("test.pb");
        Files.write(file, FileDescriptorSet.newBuilder().addFile(FILE).addFile(OTHER).build().toByteArray());
        return file;
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
}
