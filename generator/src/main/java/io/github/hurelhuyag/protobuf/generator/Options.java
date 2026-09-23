package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnknownFieldSet;

import java.util.List;

/**
 * Reads the {@code declarative/options.proto} custom options. They arrive as unknown fields — protoc has already
 * resolved and serialised them, and the generator needs no registry to read them back.
 */
final class Options {

    /** {@code (declarative.java)} on a message and {@code (declarative.java_field)} on a field. */
    private static final int JAVA = 50001;
    private static final int TYPE = 1;
    private static final int CONVERTER = 2;

    private Options() {
    }

    /** A Java type and the converter that maps the generated wire type to it. */
    record Converted(String javaType, String converterClass) {
    }

    static Converted of(MessageOptions options, String where) {
        return read(options.getUnknownFields(), where);
    }

    static Converted of(FieldOptions options, String where) {
        return read(options.getUnknownFields(), where);
    }

    private static Converted read(UnknownFieldSet unknown, String where) {
        List<ByteString> option = unknown.getField(JAVA).getLengthDelimitedList();
        if (option.isEmpty()) return null;
        UnknownFieldSet value;
        try {
            value = UnknownFieldSet.parseFrom(option.get(0));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(where + " has an unreadable declarative.JavaType option", e);
        }
        String javaType = string(value, TYPE);
        String converter = string(value, CONVERTER);
        if (javaType == null || converter == null) {
            throw new IllegalArgumentException(where + " sets only one of 'type' and 'converter' in its"
                + " declarative.JavaType option; a conversion needs both");
        }
        return new Converted(javaType, converter);
    }

    private static String string(UnknownFieldSet unknown, int number) {
        List<ByteString> values = unknown.getField(number).getLengthDelimitedList();
        return values.isEmpty() ? null : values.get(0).toStringUtf8();
    }
}
