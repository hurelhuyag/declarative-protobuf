package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.WireFormat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Emits the {@code <Record>ProtoCodec} class for one bound record. */
final class MessageCodecWriter {

    private final String packageName;
    private final String codecName;
    private final String recordType;
    private final Descriptor message;
    private final List<FieldBinding> fields;
    /** Real (non-synthetic) oneofs with at least two declared members: those need mutual exclusion. */
    private final Map<OneofDescriptor, List<FieldBinding.Singular>> oneofs = new LinkedHashMap<>();
    private final SourceWriter w = new SourceWriter();
    private static final int LENGTH_DELIMITED = WireFormat.WIRETYPE_LENGTH_DELIMITED;

    MessageCodecWriter(
        String packageName, String codecName, String recordType, Descriptor message, List<FieldBinding> fields
    ) {
        this.packageName = packageName;
        this.codecName = codecName;
        this.recordType = recordType;
        this.message = message;
        this.fields = fields;
        for (FieldBinding field : fields) {
            OneofDescriptor oneof = field.field().getRealContainingOneof();
            if (oneof != null && field instanceof FieldBinding.Singular singular) {
                oneofs.computeIfAbsent(oneof, k -> new ArrayList<>()).add(singular);
            }
        }
        oneofs.values().removeIf(members -> members.size() < 2);
    }

    String write() {
        if (!packageName.isEmpty()) w.line("package " + packageName + ";").line("");
        w.line("import com.google.protobuf.CodedInputStream;");
        w.line("import com.google.protobuf.CodedOutputStream;");
        w.line("import io.github.hurelhuyag.protobuf.internal.EmbeddedSizes;");
        w.line("import io.github.hurelhuyag.protobuf.internal.TwoPassCodec;");
        w.line("import io.github.hurelhuyag.protobuf.internal.WellKnownCodecs;");
        w.line("import io.github.hurelhuyag.protobuf.internal.Wire;");
        w.line("import java.io.IOException;");
        w.line("");
        w.line("/** Generated codec binding {@code " + message.getFullName() + "} to {@link " + recordType + "}. */");
        w.line("@javax.annotation.processing.Generated(\"" + DeclarativeProtobufProcessor.class.getName() + "\")");
        w.open("public final class " + codecName + " implements TwoPassCodec<" + recordType + "> {");
        w.line("");
        w.line("public static final " + codecName + " INSTANCE = new " + codecName + "();");
        w.line("");
        w.open("private " + codecName + "() {").close();
        writeCustomCodecFields();
        writeEmbeddedHelpers();
        writeDecode();
        writeEncode();
        writeComputeSize();
        writeOneofChecks();
        w.close();
        return w.toString();
    }

    private void writeCustomCodecFields() {
        Set<String> classes = new LinkedHashSet<>();
        for (FieldBinding field : fields) {
            if (field instanceof FieldBinding.Singular s) add(classes, s.value());
            if (field instanceof FieldBinding.Repeated r) add(classes, r.element());
            if (field instanceof FieldBinding.MapField m) {
                add(classes, m.key());
                add(classes, m.value());
            }
        }
        if (classes.isEmpty()) return;
        w.line("");
        for (String cls : classes) {
            w.line("private static final " + cls + " " + ConverterBinding.instanceName(cls) + " = new " + cls + "();");
        }
    }

    private static void add(Set<String> classes, ValueBinding binding) {
        if (binding.customCodecClass() != null) classes.add(binding.customCodecClass());
    }

    /** Static, statically typed twins of the {@code Wire} helpers; call sites in other codecs stay monomorphic. */
    private void writeEmbeddedHelpers() {
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

    private void writeDecode() {
        w.line("");
        w.line("@Override");
        w.open("public " + recordType + " decode(CodedInputStream in) throws IOException {");
        for (FieldBinding field : fields) {
            if (field instanceof FieldBinding.Singular s) {
                String init = s.presence() ? "null" : s.value().wire().defaultValue();
                w.line(s.localType() + " " + s.local() + " = " + init + ";");
            } else {
                w.line(field.declaredType() + " " + field.local() + " = null;");
            }
        }
        w.line("int tag;");
        w.open("while ((tag = in.readTag()) != 0) {");
        w.open("switch (tag) {");
        for (FieldBinding field : fields) {
            if (field instanceof FieldBinding.Singular s) decodeSingular(s);
            else if (field instanceof FieldBinding.Repeated r) decodeRepeated(r);
            else if (field instanceof FieldBinding.MapField m) decodeMap(m);
        }
        w.line("default -> in.skipField(tag);");
        w.close();
        w.close();
        if (fields.isEmpty()) {
            w.line("return new " + recordType + "();");
        } else {
            w.open("return new " + recordType + "(");
            for (int i = 0; i < fields.size(); i++) {
                FieldBinding field = fields.get(i);
                String arg = field.local();
                if (field instanceof FieldBinding.Singular s) arg = s.constructorArg();
                if (field instanceof FieldBinding.Repeated) arg += " == null ? java.util.List.of() : " + field.local();
                if (field instanceof FieldBinding.MapField) arg += " == null ? java.util.Map.of() : " + field.local();
                w.line(arg + (i < fields.size() - 1 ? "," : ""));
            }
            w.close(");");
        }
        w.close();
    }

    private void decodeSingular(FieldBinding.Singular field) {
        String comment = " // " + field.field().getName();
        List<FieldBinding.Singular> siblings = oneofs.getOrDefault(field.field().getRealContainingOneof(), List.of());
        String assign = field.local() + " = " + field.value().wire().read() + ";";
        if (siblings.size() < 2) {
            w.line("case " + tag(field.number(), field.value().wireType()) + " -> " + assign + comment);
            return;
        }
        w.open("case " + tag(field.number(), field.value().wireType()) + " -> {" + comment);
        w.line(assign);
        for (FieldBinding.Singular sibling : siblings) {
            if (sibling != field) w.line(sibling.local() + " = null;");
        }
        w.close();
    }

    private void decodeRepeated(FieldBinding.Repeated field) {
        String comment = " // " + field.field().getName();
        ValueBinding element = field.element();
        String list = field.local();
        String ensure = "if (" + list + " == null) " + list + " = new java.util.ArrayList<>();";
        if (element.packable()) {
            w.open("case " + tag(field.number(), LENGTH_DELIMITED) + " -> {" + comment + " (packed)");
            w.line("int length = in.readRawVarint32();");
            w.line("int limit = in.pushLimit(length);");
            w.line(ensure);
            w.line("while (in.getBytesUntilLimit() > 0) " + list + ".add(" + element.read() + ");");
            w.line("in.popLimit(limit);");
            w.close();
        }
        w.open("case " + tag(field.number(), element.wireType()) + " -> {" + comment);
        w.line(ensure);
        w.line(list + ".add(" + element.read() + ");");
        w.close();
    }

    private void decodeMap(FieldBinding.MapField field) {
        String map = field.local();
        w.open("case " + tag(field.number(), LENGTH_DELIMITED) + " -> { // " + field.field().getName());
        w.line("int length = in.readRawVarint32();");
        w.line("int limit = in.pushLimit(length);");
        w.line(field.key().primitiveType() + " key = " + field.key().defaultValue() + ";");
        w.line(field.value().primitiveType() + " val = " + field.value().defaultValue() + ";");
        w.line("int entryTag;");
        w.open("while ((entryTag = in.readTag()) != 0) {");
        w.open("switch (entryTag) {");
        w.line("case " + tag(1, field.key().wireType()) + " -> key = " + field.key().read() + ";");
        w.line("case " + tag(2, field.value().wireType()) + " -> val = " + field.value().read() + ";");
        w.line("default -> in.skipField(entryTag);");
        w.close();
        w.close();
        w.line("in.popLimit(limit);");
        w.line("if (" + map + " == null) " + map + " = new java.util.LinkedHashMap<>();");
        w.line(map + ".put(key, val);");
        w.close();
    }

    private void writeEncode() {
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
        for (OneofDescriptor oneof : oneofs.keySet()) w.line("checkOneof_" + oneof.getName() + "(value);");
        for (FieldBinding field : fields) {
            w.open("{ // " + field.field().getName());
            w.line(field.declaredType() + " x = value." + field.accessor() + "();");
            if (field instanceof FieldBinding.Singular s) {
                w.line("if (" + singularCondition(s) + ") " + s.value().wire().write(s.number(), singularVar(s)) + ";");
            } else if (field instanceof FieldBinding.Repeated r && r.packed() && r.element().packable()) {
                w.open("if (x != null && !x.isEmpty()) {");
                w.line("int dataSize = 0;");
                w.line("for (int i = 0, n = x.size(); i < n; i++) dataSize += "
                    + r.element().sizeNoTag("x.get(i)") + ";");
                w.line("out.writeTag(" + r.number() + ", " + LENGTH_DELIMITED + ");");
                w.line("out.writeUInt32NoTag(dataSize);");
                w.line("for (int i = 0, n = x.size(); i < n; i++) " + r.element().writeNoTag("x.get(i)") + ";");
                w.close();
            } else if (field instanceof FieldBinding.Repeated r) {
                w.line("if (x != null) for (int i = 0, n = x.size(); i < n; i++) "
                    + r.element().write(r.number(), "x.get(i)") + ";");
            } else if (field instanceof FieldBinding.MapField m) {
                w.open("if (x != null) for (java.util.Map.Entry<" + m.key().boxedType() + ", " + m.value().boxedType()
                    + "> e : x.entrySet()) {");
                mapEntryLocals(m);
                ValueBinding val = m.value().wire();
                w.line("out.writeTag(" + m.number() + ", " + LENGTH_DELIMITED + ");");
                w.line("out.writeUInt32NoTag(sizes.next());");
                w.line(m.key().write(1, "key") + ";");
                w.line("if (val != null) " + val.write(2, "val") + ";");
                w.close();
            }
            w.close();
        }
        w.close();
    }

    private void writeComputeSize() {
        w.line("");
        w.line("@Override");
        w.open("public int computeSize(" + recordType + " value) {");
        w.line("return computeSize(value, new EmbeddedSizes());");
        w.close();
        w.line("");
        w.line("@Override");
        w.open("public int computeSize(" + recordType + " value, EmbeddedSizes sizes) {");
        for (OneofDescriptor oneof : oneofs.keySet()) w.line("checkOneof_" + oneof.getName() + "(value);");
        w.line("int size = 0;");
        for (FieldBinding field : fields) {
            w.open("{ // " + field.field().getName());
            w.line(field.declaredType() + " x = value." + field.accessor() + "();");
            if (field instanceof FieldBinding.Singular s) {
                String sizeExpr = s.value().wire().size(s.number(), singularVar(s));
                w.line("if (" + singularCondition(s) + ") size += " + sizeExpr + ";");
            } else if (field instanceof FieldBinding.Repeated r && r.packed() && r.element().packable()) {
                w.open("if (x != null && !x.isEmpty()) {");
                w.line("int dataSize = 0;");
                w.line("for (int i = 0, n = x.size(); i < n; i++) dataSize += "
                    + r.element().sizeNoTag("x.get(i)") + ";");
                w.line("size += CodedOutputStream.computeTagSize(" + r.number()
                    + ") + CodedOutputStream.computeUInt32SizeNoTag(dataSize) + dataSize;");
                w.close();
            } else if (field instanceof FieldBinding.Repeated r) {
                w.line("if (x != null) for (int i = 0, n = x.size(); i < n; i++) size += "
                    + r.element().size(r.number(), "x.get(i)") + ";");
            } else if (field instanceof FieldBinding.MapField m) {
                w.open("if (x != null) for (java.util.Map.Entry<" + m.key().boxedType() + ", " + m.value().boxedType()
                    + "> e : x.entrySet()) {");
                mapEntryLocals(m);
                ValueBinding val = m.value().wire();
                w.line("int slot = sizes.reserve();");
                w.line("int entrySize = " + m.key().size(1, "key") + " + (val != null ? " + val.size(2, "val")
                    + " : 0);");
                w.line("sizes.set(slot, entrySize);");
                w.line("size += CodedOutputStream.computeTagSize(" + m.number()
                    + ") + CodedOutputStream.computeUInt32SizeNoTag(entrySize) + entrySize;");
                w.close();
            }
            w.close();
        }
        w.line("return size;");
        w.close();
    }

    /**
     * Declares {@code w}, the wire-side value of a converted singular field, after {@code x} has been declared;
     * returns the variable the write/size fragments should use.
     */
    private String singularVar(FieldBinding.Singular field) {
        ValueBinding value = field.value();
        if (value.wire() == value) return "x";
        w.line(value.wire().boxedType() + " w = x == null ? null : " + value.toWire("x") + ";");
        return "w";
    }

    private static String singularCondition(FieldBinding.Singular field) {
        ValueBinding value = field.value();
        if (field.presence()) return value.wire() == value ? "x != null" : "w != null";
        if (value.wire() == value) return value.isSet("x");
        ValueBinding wire = value.wire();
        // isSet of a reference-typed wire value already tolerates null; a boxed primitive needs the guard
        boolean reference = wire.primitiveType().equals(wire.boxedType());
        return reference ? wire.isSet("w") : "w != null && " + wire.isSet("w");
    }

    /** Declares {@code key} and {@code val} (wire-side, converted once) for a map entry {@code e}. */
    private void mapEntryLocals(FieldBinding.MapField field) {
        w.line(field.key().boxedType() + " key = e.getKey();");
        ValueBinding value = field.value();
        if (value.wire() == value) {
            w.line(value.boxedType() + " val = e.getValue();");
        } else {
            w.line(value.boxedType() + " raw = e.getValue();");
            w.line(value.wire().boxedType() + " val = raw == null ? null : " + value.toWire("raw") + ";");
        }
    }

    private void writeOneofChecks() {
        for (Map.Entry<OneofDescriptor, List<FieldBinding.Singular>> entry : oneofs.entrySet()) {
            String members = entry.getValue().stream().map(FieldBinding::accessor).collect(Collectors.joining(", "));
            w.line("");
            w.open("private static void checkOneof_" + entry.getKey().getName() + "(" + recordType + " value) {");
            w.line("int set = 0;");
            for (FieldBinding.Singular member : entry.getValue()) {
                w.line("if (value." + member.accessor() + "() != null) set++;");
            }
            w.line("if (set > 1) throw new IllegalArgumentException(\"" + recordType + ": at most one of oneof '"
                + entry.getKey().getName() + "' members [" + members + "] may be non-null\");");
            w.close();
        }
    }
}
