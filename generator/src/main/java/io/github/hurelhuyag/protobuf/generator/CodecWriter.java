package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.WireFormat;

import java.util.List;

/** Emits the codec for one message: the wire work for the record {@link RecordWriter} generated. */
final class CodecWriter {

    private static final int LENGTH_DELIMITED = WireFormat.WIRETYPE_LENGTH_DELIMITED;

    private final Descriptor message;
    private final String recordType;
    private final String codecName;
    private final List<Field> fields;
    private final SourceWriter w = new SourceWriter();

    private CodecWriter(Descriptor message, Customizations customizations) {
        this.message = message;
        this.recordType = Names.javaName(message, customizations);
        this.codecName = simpleName(Names.codecName(message, customizations));
        this.fields = TypeMapper.fields(message, customizations);
    }

    static String write(Descriptor message, Customizations customizations) {
        return new CodecWriter(message, customizations).write();
    }

    private String write() {
        String packageName = Names.javaPackage(message.getFile());
        if (!packageName.isEmpty()) w.line("package " + packageName + ";").line("");
        w.line("import com.google.protobuf.CodedInputStream;");
        w.line("import com.google.protobuf.CodedOutputStream;");
        w.line("import io.github.hurelhuyag.protobuf.internal.EmbeddedSizes;");
        w.line("import io.github.hurelhuyag.protobuf.internal.TwoPassCodec;");
        w.line("import io.github.hurelhuyag.protobuf.internal.WellKnownCodecs;");
        w.line("import io.github.hurelhuyag.protobuf.internal.Wire;");
        w.line("import java.io.IOException;");
        w.line("import javax.annotation.processing.Generated;");
        w.line("");
        w.line("/** Generated codec for {@code " + message.getFullName() + "}. */");
        w.line("@Generated(\"" + Generator.NAME + "\")");
        w.open("public final class " + codecName + " implements TwoPassCodec<" + recordType + "> {");
        w.line("");
        w.line("public static final " + codecName + " INSTANCE = new " + codecName + "();");
        w.line("");
        w.open("private " + codecName + "() {").close();
        converterFields();
        embeddedHelpers();
        decode();
        encode();
        computeSize();
        w.close();
        return w.toString();
    }

    /** One shared instance per converter the schema attached to a field of this message. */
    private void converterFields() {
        java.util.Set<String> classes = new java.util.LinkedHashSet<>();
        for (Field field : fields) {
            if (field instanceof Field.Singular s) add(classes, s.value());
            else if (field instanceof Field.Repeated r) add(classes, r.element());
            else if (field instanceof Field.MapField m) {
                add(classes, m.key());
                add(classes, m.value());
            } else if (field instanceof Field.Oneof o) {
                for (Field.Oneof.Case member : o.cases()) add(classes, member.value());
            }
        }
        if (classes.isEmpty()) return;
        w.line("");
        for (String converter : classes) {
            w.line("private static final " + converter + " " + ConverterValue.instanceName(converter)
                + " = new " + converter + "();");
        }
    }

    private static void add(java.util.Set<String> classes, Value value) {
        if (value.converterClass() != null) classes.add(value.converterClass());
    }

    /** Static, statically typed twins of the {@code Wire} helpers; call sites in other codecs stay monomorphic. */
    private void embeddedHelpers() {
        w.line("");
        w.line("/** Reads a length-delimited embedded message whose tag has already been consumed. */");
        w.open("public static " + recordType + " readEmbedded(CodedInputStream in) throws IOException {");
        w.line("int length = in.readRawVarint32();");
        w.line("int oldLimit = in.pushLimit(length);");
        w.line(recordType + " value = INSTANCE.decode(in);");
        w.line("in.checkLastTagWas(0);");
        w.line("in.popLimit(oldLimit);");
        w.line("return value;");
        w.close();
        w.line("");
        w.line("/** Size pass: sizes an embedded message and records it for {@link #writeEmbedded}. */");
        w.open("public static int embeddedSize(int fieldNumber, " + recordType + " value, EmbeddedSizes sizes) {");
        w.line("int slot = sizes.reserve();");
        w.line("int size = INSTANCE.computeSize(value, sizes);");
        w.line("sizes.set(slot, size);");
        w.line("return CodedOutputStream.computeTagSize(fieldNumber) + CodedOutputStream.computeUInt32SizeNoTag(size)"
            + " + size;");
        w.close();
        w.line("");
        w.line("/** Encode pass: writes an embedded message with the size recorded by {@link #embeddedSize}. */");
        w.open("public static void writeEmbedded(CodedOutputStream out, int fieldNumber, " + recordType + " value,"
            + " EmbeddedSizes sizes) throws IOException {");
        w.line("out.writeTag(fieldNumber, " + LENGTH_DELIMITED + ");");
        w.line("out.writeUInt32NoTag(sizes.next());");
        w.line("INSTANCE.encode(value, out, sizes);");
        w.close();
    }

    private static int tag(int fieldNumber, int wireType) {
        return (fieldNumber << 3) | wireType;
    }

    private void decode() {
        w.line("");
        w.line("@Override");
        w.open("public " + recordType + " decode(CodedInputStream in) throws IOException {");
        for (Field field : fields) {
            String init = field instanceof Field.Singular s && !s.presence() ? s.value().wire().defaultValue() : "null";
            w.line(localType(field) + " " + field.local() + " = " + init + ";");
        }
        w.line("int tag;");
        w.open("while ((tag = in.readTag()) != 0) {");
        w.open("switch (tag) {");
        for (Field field : fields) {
            if (field instanceof Field.Singular s) {
                w.line("case " + tag(s.number(), s.value().wireType()) + " -> " + s.local() + " = "
                    + s.value().wire().read() + "; // " + s.protoName());
            } else if (field instanceof Field.Repeated r) {
                decodeRepeated(r);
            } else if (field instanceof Field.MapField m) {
                decodeMap(m);
            } else if (field instanceof Field.Oneof o) {
                for (Field.Oneof.Case member : o.cases()) {
                    w.line("case " + tag(member.number(), member.value().wireType()) + " -> " + o.local() + " = new "
                        + member.type() + "(" + member.value().read() + "); // " + member.descriptor().getName());
                }
            }
        }
        w.line("default -> in.skipField(tag);");
        w.close();
        w.close();
        if (fields.isEmpty()) {
            w.line("return new " + recordType + "();");
        } else {
            w.open("return new " + recordType + "(");
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                String arg = field instanceof Field.Singular s ? s.constructorArg() : field.local();
                w.line(arg + (i < fields.size() - 1 ? "," : ""));
            }
            w.close(");");
        }
        w.close();
    }

    /** Repeated and map locals stay null until the first element, so an absent field allocates nothing. */
    private String localType(Field field) {
        if (field instanceof Field.Singular s) return s.localType();
        return field.type();
    }

    private void decodeRepeated(Field.Repeated field) {
        String list = field.local();
        String ensure = "if (" + list + " == null) " + list + " = new java.util.ArrayList<>();";
        if (field.element().packable()) {
            w.open("case " + tag(field.number(), LENGTH_DELIMITED) + " -> { // " + field.protoName() + " (packed)");
            w.line("int length = in.readRawVarint32();");
            w.line("int limit = in.pushLimit(length);");
            w.line(ensure);
            w.line("while (in.getBytesUntilLimit() > 0) " + list + ".add(" + field.element().read() + ");");
            w.line("in.popLimit(limit);");
            w.close();
        }
        w.open("case " + tag(field.number(), field.element().wireType()) + " -> { // " + field.protoName());
        w.line(ensure);
        w.line(list + ".add(" + field.element().read() + ");");
        w.close();
    }

    private void decodeMap(Field.MapField field) {
        String map = field.local();
        w.open("case " + tag(field.number(), LENGTH_DELIMITED) + " -> { // " + field.protoName());
        w.line("int length = in.readRawVarint32();");
        w.line("int limit = in.pushLimit(length);");
        w.line(field.key().wire().type() + " key = " + field.key().wire().defaultValue() + ";");
        w.line(field.value().wire().type() + " val = " + field.value().wire().defaultValue() + ";");
        w.line("int entryTag;");
        w.open("while ((entryTag = in.readTag()) != 0) {");
        w.open("switch (entryTag) {");
        w.line("case " + tag(1, field.key().wireType()) + " -> key = " + field.key().wire().read() + ";");
        w.line("case " + tag(2, field.value().wireType()) + " -> val = " + field.value().wire().read() + ";");
        w.line("default -> in.skipField(entryTag);");
        w.close();
        w.close();
        w.line("in.popLimit(limit);");
        w.line("if (" + map + " == null) " + map + " = new java.util.LinkedHashMap<>();");
        w.line(map + ".put(" + field.key().fromWire("key") + ", " + field.value().fromWire("val") + ");");
        w.close();
    }

    private void encode() {
        w.line("");
        w.line("@Override");
        w.open("public void encode(" + recordType + " value, CodedOutputStream out) throws IOException {");
        w.line("EmbeddedSizes sizes = new EmbeddedSizes();");
        w.line("computeSize(value, sizes);");
        w.line("encode(value, out, sizes.rewind());");
        w.close();
        w.line("");
        w.line("@Override");
        w.open("public void encode(" + recordType + " value, CodedOutputStream out, EmbeddedSizes sizes)"
            + " throws IOException {");
        for (Field field : fields) {
            w.open("{ // " + field.protoName());
            w.line(field.type() + " x = value." + field.name() + "();");
            if (field instanceof Field.Singular s) {
                String variable = singularVar(s);
                w.line("if (" + singularCondition(s) + ") " + s.value().wire().write(s.number(), variable) + ";");
            } else if (field instanceof Field.Repeated r && r.packed() && r.element().packable()) {
                w.open("if (!x.isEmpty()) {");
                w.line("int dataSize = 0;");
                w.line("for (int i = 0, n = x.size(); i < n; i++) dataSize += "
                    + r.element().sizeNoTag("x.get(i)") + ";");
                w.line("out.writeTag(" + r.number() + ", " + LENGTH_DELIMITED + ");");
                w.line("out.writeUInt32NoTag(dataSize);");
                w.line("for (int i = 0, n = x.size(); i < n; i++) " + r.element().writeNoTag("x.get(i)") + ";");
                w.close();
            } else if (field instanceof Field.Repeated r) {
                w.line("for (int i = 0, n = x.size(); i < n; i++) " + r.element().write(r.number(), "x.get(i)") + ";");
            } else if (field instanceof Field.MapField m) {
                w.open("for (java.util.Map.Entry<" + m.key().boxedType() + ", " + m.value().boxedType()
                    + "> e : x.entrySet()) {");
                mapEntryLocals(m);
                w.line("out.writeTag(" + m.number() + ", " + LENGTH_DELIMITED + ");");
                w.line("out.writeUInt32NoTag(sizes.next());");
                w.line(m.key().wire().write(1, "key") + ";");
                w.line("if (val != null) " + m.value().wire().write(2, "val") + ";");
                w.close();
            } else if (field instanceof Field.Oneof o) {
                // a oneof member is set by construction, so it is written even when it holds the default value
                oneofChain(o, member -> member.value().write(member.number(), "c.value()") + ";");
            }
            w.close();
        }
        w.close();
    }

    private void computeSize() {
        w.line("");
        w.line("@Override");
        w.open("public int computeSize(" + recordType + " value) {");
        w.line("return computeSize(value, new EmbeddedSizes());");
        w.close();
        w.line("");
        w.line("@Override");
        w.open("public int computeSize(" + recordType + " value, EmbeddedSizes sizes) {");
        w.line("int size = 0;");
        for (Field field : fields) {
            w.open("{ // " + field.protoName());
            w.line(field.type() + " x = value." + field.name() + "();");
            if (field instanceof Field.Singular s) {
                String sizeExpr = s.value().wire().size(s.number(), singularVar(s));
                w.line("if (" + singularCondition(s) + ") size += " + sizeExpr + ";");
            } else if (field instanceof Field.Repeated r && r.packed() && r.element().packable()) {
                w.open("if (!x.isEmpty()) {");
                w.line("int dataSize = 0;");
                w.line("for (int i = 0, n = x.size(); i < n; i++) dataSize += "
                    + r.element().sizeNoTag("x.get(i)") + ";");
                w.line("size += CodedOutputStream.computeTagSize(" + r.number()
                    + ") + CodedOutputStream.computeUInt32SizeNoTag(dataSize) + dataSize;");
                w.close();
            } else if (field instanceof Field.Repeated r) {
                w.line("for (int i = 0, n = x.size(); i < n; i++) size += "
                    + r.element().size(r.number(), "x.get(i)") + ";");
            } else if (field instanceof Field.MapField m) {
                w.open("for (java.util.Map.Entry<" + m.key().boxedType() + ", " + m.value().boxedType()
                    + "> e : x.entrySet()) {");
                mapEntryLocals(m);
                w.line("int slot = sizes.reserve();");
                w.line("int entrySize = " + m.key().wire().size(1, "key") + " + (val != null ? "
                    + m.value().wire().size(2, "val") + " : 0);");
                w.line("sizes.set(slot, entrySize);");
                w.line("size += CodedOutputStream.computeTagSize(" + m.number()
                    + ") + CodedOutputStream.computeUInt32SizeNoTag(entrySize) + entrySize;");
                w.close();
            } else if (field instanceof Field.Oneof o) {
                oneofChain(o, member -> "size += " + member.value().size(member.number(), "c.value()") + ";");
            }
            w.close();
        }
        w.line("return size;");
        w.close();
    }

    /**
     * The {@code instanceof} chain over a oneof's cases. Encode and size emit it in the same order, which is what
     * lets the size pass and the encode pass agree on the embedded-size table.
     */
    private void oneofChain(Field.Oneof oneof, java.util.function.Function<Field.Oneof.Case, String> body) {
        for (int i = 0; i < oneof.cases().size(); i++) {
            Field.Oneof.Case member = oneof.cases().get(i);
            String prefix = i == 0 ? "if" : "else if";
            w.line(prefix + " (x instanceof " + member.type() + " c) " + body.apply(member));
        }
    }

    /**
     * Declares {@code w}, the wire-side value of a converted singular field, after {@code x}; returns the
     * variable the write and size fragments should use.
     */
    private String singularVar(Field.Singular field) {
        Value value = field.value();
        if (value.wire() == value) return "x";
        w.line(value.wire().boxedType() + " w = x == null ? null : " + value.toWire("x") + ";");
        return "w";
    }

    private static String singularCondition(Field.Singular field) {
        Value value = field.value();
        if (field.presence()) return value.wire() == value ? "x != null" : "w != null";
        if (value.wire() == value) return value.isSet("x");
        Value wire = value.wire();
        // a reference-typed wire value tolerates null in its own check; a boxed primitive needs the guard
        boolean reference = wire.type().equals(wire.boxedType());
        return reference ? wire.isSet("w") : "w != null && " + wire.isSet("w");
    }

    /** Declares {@code key} and {@code val} for a map entry {@code e}, converted once. */
    private void mapEntryLocals(Field.MapField field) {
        w.line(field.key().wire().boxedType() + " key = " + field.key().toWire("e.getKey()") + ";");
        Value value = field.value();
        if (value.wire() == value) {
            w.line(value.boxedType() + " val = e.getValue();");
        } else {
            w.line(value.boxedType() + " raw = e.getValue();");
            w.line(value.wire().boxedType() + " val = raw == null ? null : " + value.toWire("raw") + ";");
        }
    }

    private static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }
}
