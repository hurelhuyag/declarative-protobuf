package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import io.github.hurelhuyag.protobuf.Proto;
import io.github.hurelhuyag.protobuf.ProtoConverter;
import io.github.hurelhuyag.protobuf.ProtoEnum;
import io.github.hurelhuyag.protobuf.ProtoMessage;
import io.github.hurelhuyag.protobuf.ProtoUnrecognized;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/** Validates annotated records and enums against the schema and produces the binding model for generation. */
final class Binder {

    /** Java types a {@code google.protobuf.*} message field may be declared as, with the built-in codec for each. */
    private static final Map<String, List<MessageBinding>> WELL_KNOWN = Map.ofEntries(
        wellKnown("google.protobuf.Timestamp", "java.time.Instant", "TIMESTAMP"),
        wellKnown("google.protobuf.Duration", "java.time.Duration", "DURATION"),
        wellKnown("google.protobuf.DoubleValue", "java.lang.Double", "DOUBLE_VALUE"),
        wellKnown("google.protobuf.FloatValue", "java.lang.Float", "FLOAT_VALUE"),
        wellKnown("google.protobuf.Int64Value", "java.lang.Long", "INT64_VALUE"),
        wellKnown("google.protobuf.UInt64Value", "java.lang.Long", "UINT64_VALUE"),
        wellKnown("google.protobuf.Int32Value", "java.lang.Integer", "INT32_VALUE"),
        wellKnown("google.protobuf.UInt32Value", "java.lang.Integer", "UINT32_VALUE"),
        wellKnown("google.protobuf.BoolValue", "java.lang.Boolean", "BOOL_VALUE"),
        wellKnown("google.protobuf.StringValue", "java.lang.String", "STRING_VALUE"),
        Map.entry("google.protobuf.BytesValue", List.of(
            new MessageBinding("com.google.protobuf.ByteString", "WellKnownCodecs.BYTES_VALUE", false),
            new MessageBinding("byte[]", "WellKnownCodecs.BYTE_ARRAY_VALUE", false)
        ))
    );

    private static Map.Entry<String, List<MessageBinding>> wellKnown(String message, String javaType, String codec) {
        return Map.entry(message, List.of(new MessageBinding(javaType, "WellKnownCodecs." + codec, false)));
    }

    private static final String CONVERTER = "io.github.hurelhuyag.protobuf.ProtoConverter";

    private final Schema schema;
    private final Messager messager;
    private final Elements elements;
    private final Types types;
    private final Filer filer;
    /** User codecs discovered through META-INF/services, resolved on first use so errors attach to a record. */
    private List<TypeMirror> registeredCodecs;

    Binder(ProcessingEnvironment env, Schema schema) {
        this.schema = schema;
        this.messager = env.getMessager();
        this.elements = env.getElementUtils();
        this.types = env.getTypeUtils();
        this.filer = env.getFiler();
    }

    /** Converters named in {@code META-INF/services}; each must be on the compile classpath and implement it. */
    private List<TypeMirror> registeredCodecs(Element reportAt) {
        if (registeredCodecs != null) return registeredCodecs;
        registeredCodecs = new ArrayList<>();
        for (String name : serviceProviders(ProtoConverter.class, reportAt)) {
            TypeElement codec = elements.getTypeElement(name);
            if (codec == null) {
                error(reportAt, "registered converter class '" + name + "' is not on the compile classpath");
            } else if (supertype(codec.asType(), CONVERTER) == null) {
                error(reportAt, "registered converter class '" + name + "' does not implement ProtoConverter");
            } else {
                registeredCodecs.add(codec.asType());
            }
        }
        return registeredCodecs;
    }

    /**
     * Implementations named in {@code META-INF/services/<service>}: those visible to the processor's own class
     * loader (jars on the annotation processor path), plus the first such file on the compile classpath, which is
     * all the {@link Filer} can see (typically the module being compiled, or one dependency).
     */
    private Set<String> serviceProviders(Class<?> service, Element reportAt) {
        Set<String> names = new LinkedHashSet<>();
        try {
            ServiceLoader.load(service, Binder.class.getClassLoader()).stream()
                .forEach(provider -> names.add(provider.type().getName()));
        } catch (ServiceConfigurationError e) {
            error(reportAt, "cannot load META-INF/services/" + service.getName() + " from the processor path: "
                + e.getMessage());
        }
        try {
            String path = "META-INF/services/" + service.getName();
            FileObject file = filer.getResource(StandardLocation.CLASS_PATH, "", path);
            try (BufferedReader reader = new BufferedReader(file.openReader(true))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int comment = line.indexOf('#');
                    String name = (comment < 0 ? line : line.substring(0, comment)).trim();
                    if (!name.isEmpty()) names.add(name);
                }
            }
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException e) {
            // no such resource on the compile classpath, or the compiler does not expose it; nothing to register
        }
        return names;
    }

    /**
     * The registered converter whose target is {@code type} and whose wire type fits {@code field}. Empty when
     * none fits; reports an error and returns null when several do.
     */
    private Optional<TypeMirror> registeredCodecFor(RecordComponentElement at, FieldDescriptor field, TypeMirror type) {
        List<TypeMirror> fitting = new ArrayList<>();
        for (TypeMirror codec : registeredCodecs(at)) {
            List<? extends TypeMirror> args = supertype(codec, CONVERTER).getTypeArguments();
            if (types.isSameType(args.get(1), type) && wireTypeFits(field, args.get(0))) fitting.add(codec);
        }
        if (fitting.size() > 1) {
            error(at, "several registered converters fit component '" + at.getSimpleName() + "' (" + type + "): "
                + fitting + "; a component type can have only one fitting converter");
            return null;
        }
        return fitting.stream().findFirst();
    }

    /** Whether a converter with wire type {@code wire} can serve {@code field} (checked without reporting). */
    private boolean wireTypeFits(FieldDescriptor field, TypeMirror wire) {
        return switch (field.getJavaType()) {
            case INT, LONG, FLOAT, DOUBLE, BOOLEAN -> isSame(wire, Scalar.of(field.getType()).boxedType());
            case STRING -> isSame(wire, "java.lang.String");
            case BYTE_STRING -> isByteArray(wire) || isSame(wire, "com.google.protobuf.ByteString");
            case ENUM -> isSame(wire, "java.lang.Integer");
            case MESSAGE -> {
                String expected = field.getMessageType().getFullName();
                TypeElement record = typeElement(wire);
                if (record != null && record.getAnnotation(ProtoMessage.class) != null) {
                    Descriptor bound = boundMessage(record);
                    yield bound != null && bound.getFullName().equals(expected);
                }
                for (MessageBinding candidate : WELL_KNOWN.getOrDefault(expected, List.of())) {
                    boolean matches = candidate.javaType().equals("byte[]")
                        ? isByteArray(wire)
                        : isSame(wire, candidate.javaType());
                    if (matches) yield true;
                }
                yield false;
            }
        };
    }

    /** Like {@link #message(TypeElement)} but silent: the message a record's {@code @ProtoMessage} names, or null. */
    private Descriptor boundMessage(TypeElement record) {
        String name = record.getAnnotation(ProtoMessage.class).value();
        if (!name.isEmpty()) return schema.message(name);
        List<Descriptor> candidates = schema.messagesNamed(record.getSimpleName().toString());
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    static String codecClass(Elements elements, TypeElement type) {
        return elements.getBinaryName(type).toString().replace('$', '_') + "ProtoCodec";
    }

    /** Resolves the message a record is bound to, reporting an error and returning null when it cannot. */
    Descriptor message(TypeElement record) {
        String name = record.getAnnotation(ProtoMessage.class).value();
        if (!name.isEmpty()) {
            Descriptor message = schema.message(name);
            if (message == null) error(record, "no message '" + name + "' in the descriptor set");
            return message;
        }
        List<Descriptor> candidates = schema.messagesNamed(record.getSimpleName().toString());
        if (candidates.size() == 1) return candidates.get(0);
        if (candidates.isEmpty()) {
            error(record, "no message named '" + record.getSimpleName() + "' in the descriptor set; give @ProtoMessage"
                + " the fully qualified message name");
        } else {
            error(record, "message name '" + record.getSimpleName() + "' is ambiguous: "
                + candidates.stream().map(Descriptor::getFullName).toList() + "; give @ProtoMessage the fully qualified"
                + " message name");
        }
        return null;
    }

    EnumDescriptor enumType(TypeElement enumElement) {
        String name = enumElement.getAnnotation(ProtoEnum.class).value();
        if (!name.isEmpty()) {
            EnumDescriptor enumType = schema.enumType(name);
            if (enumType == null) error(enumElement, "no enum '" + name + "' in the descriptor set");
            return enumType;
        }
        List<EnumDescriptor> candidates = schema.enumsNamed(enumElement.getSimpleName().toString());
        if (candidates.size() == 1) return candidates.get(0);
        if (candidates.isEmpty()) {
            error(enumElement, "no enum named '" + enumElement.getSimpleName() + "' in the descriptor set; give"
                + " @ProtoEnum the fully qualified enum name");
        } else {
            error(enumElement, "enum name '" + enumElement.getSimpleName() + "' is ambiguous: "
                + candidates.stream().map(EnumDescriptor::getFullName).toList() + "; give @ProtoEnum the fully"
                + " qualified enum name");
        }
        return null;
    }

    /** Validates an enum's constants; returns constants by value number plus the unrecognized one, or null. */
    EnumModel enumConstants(TypeElement enumElement, EnumDescriptor enumType) {
        boolean ok = true;
        Map<Integer, String> byNumber = new LinkedHashMap<>();
        String unrecognized = null;
        for (Element member : enumElement.getEnclosedElements()) {
            if (member.getKind() != ElementKind.ENUM_CONSTANT) continue;
            String constant = member.getSimpleName().toString();
            Proto proto = member.getAnnotation(Proto.class);
            boolean fallback = member.getAnnotation(ProtoUnrecognized.class) != null;
            if (fallback) {
                if (proto != null) {
                    error(member, "a @ProtoUnrecognized constant carries no @Proto number");
                    ok = false;
                }
                if (unrecognized != null) {
                    error(member, "only one constant may be @ProtoUnrecognized (already '" + unrecognized + "')");
                    ok = false;
                }
                unrecognized = constant;
                continue;
            }
            if (proto == null) {
                error(member, "constant '" + constant + "' has no @Proto value number");
                ok = false;
                continue;
            }
            if (enumType.findValueByNumber(proto.value()) == null) {
                boolean reserved = enumType.toProto().getReservedRangeList().stream()
                    .anyMatch(r -> r.getStart() <= proto.value() && proto.value() <= r.getEnd());
                error(member, "enum '" + enumType.getFullName() + "' has no value " + proto.value()
                    + (reserved ? " (it is reserved)" : ""));
                ok = false;
            }
            String previous = byNumber.putIfAbsent(proto.value(), constant);
            if (previous != null) {
                error(member, "value " + proto.value() + " is already bound to constant '" + previous + "'");
                ok = false;
            }
        }
        for (EnumValueDescriptor value : enumType.getValues()) {
            if (!byNumber.containsKey(value.getNumber())) {
                error(enumElement, "enum '" + enumType.getFullName() + "' value " + value.getName() + " = "
                    + value.getNumber() + " has no constant with @Proto(" + value.getNumber() + ")");
                ok = false;
            }
        }
        if (unrecognized == null) {
            messager.printMessage(Kind.WARNING, "enum '" + enumType.getFullName() + "' has no @ProtoUnrecognized"
                + " constant; decoding a value number added to the schema later will fail", enumElement);
        }
        return ok ? new EnumModel(byNumber, unrecognized) : null;
    }

    record EnumModel(Map<Integer, String> constantsByNumber, String unrecognized) {
    }

    /** Binds every component of the record to a schema field; returns null (after reporting) on any error. */
    List<FieldBinding> fields(TypeElement record, Descriptor message) {
        boolean ok = true;
        if (record.getKind() != ElementKind.RECORD) {
            error(record, "@ProtoMessage is only supported on records");
            return null;
        }
        if (record.getModifiers().contains(Modifier.PRIVATE) || !constructorAccessible(record)) {
            error(record, "record and its canonical constructor must be accessible from package '"
                + elements.getPackageOf(record).getQualifiedName() + "'");
            ok = false;
        }
        List<FieldBinding> bindings = new ArrayList<>();
        Map<Integer, RecordComponentElement> seen = new HashMap<>();
        for (RecordComponentElement component : record.getRecordComponents()) {
            Proto proto = component.getAnnotation(Proto.class);
            if (proto == null) {
                error(component, "component '" + component.getSimpleName() + "' has no @Proto field number");
                ok = false;
                continue;
            }
            FieldDescriptor field = message.findFieldByNumber(proto.value());
            if (field == null) {
                boolean reserved = message.toProto().getReservedRangeList().stream()
                    .anyMatch(r -> r.getStart() <= proto.value() && proto.value() < r.getEnd());
                error(component, "message '" + message.getFullName() + "' has no field number " + proto.value()
                    + (reserved ? " (it is reserved)" : ""));
                ok = false;
                continue;
            }
            if (field.getType() == FieldDescriptor.Type.GROUP) {
                error(component, "field '" + field.getFullName() + "' uses delimited (group) message encoding, which"
                    + " is not supported");
                ok = false;
                continue;
            }
            RecordComponentElement previous = seen.putIfAbsent(proto.value(), component);
            if (previous != null) {
                error(component, "field " + proto.value() + " is already bound to component '"
                    + previous.getSimpleName() + "'");
                ok = false;
                continue;
            }
            FieldBinding binding = field(component, field);
            if (binding == null) ok = false; else bindings.add(binding);
        }
        return ok ? bindings : null;
    }

    private boolean constructorAccessible(TypeElement record) {
        int arity = record.getRecordComponents().size();
        for (Element member : record.getEnclosedElements()) {
            if (member.getKind() == ElementKind.CONSTRUCTOR
                && ((ExecutableElement) member).getParameters().size() == arity) {
                return !member.getModifiers().contains(Modifier.PRIVATE);
            }
        }
        return true;
    }

    private FieldBinding field(RecordComponentElement component, FieldDescriptor field) {
        TypeMirror type = component.asType();
        if (field.isMapField()) {
            DeclaredType map = declaredOf(type, "java.util.Map");
            if (map == null || map.getTypeArguments().size() != 2) {
                return mismatch(component, field, type, "'java.util.Map<K, V>'");
            }
            Descriptor entry = field.getMessageType();
            ValueBinding key = value(component, entry.findFieldByNumber(1), map.getTypeArguments().get(0), true);
            ValueBinding value = value(component, entry.findFieldByNumber(2), map.getTypeArguments().get(1), true);
            return key == null || value == null ? null : new FieldBinding.MapField(component, field, key, value);
        }
        if (field.isRepeated()) {
            DeclaredType list = declaredOf(type, "java.util.List");
            if (list == null || list.getTypeArguments().size() != 1) {
                return mismatch(component, field, type, "'java.util.List<E>'");
            }
            ValueBinding element = value(component, field, list.getTypeArguments().get(0), true);
            return element == null ? null : new FieldBinding.Repeated(component, field, element, field.isPacked());
        }
        boolean presence = field.hasPresence();
        ValueBinding value = value(component, field, type, presence);
        return value == null ? null : new FieldBinding.Singular(component, field, value, presence);
    }

    /** Binds through a registered converter: the wire-side type is bound as usual, the converter wraps it. */
    private ValueBinding custom(RecordComponentElement at, FieldDescriptor field, TypeMirror type, TypeMirror codec) {
        TypeElement codecElement = typeElement(codec);
        String codecName = codecElement.getQualifiedName().toString();
        if (codecElement.getKind() != ElementKind.CLASS || codecElement.getModifiers().contains(Modifier.ABSTRACT)) {
            error(at, "converter '" + codecName + "' must be a concrete class");
            return null;
        }
        boolean samePackage = elements.getPackageOf(codecElement).equals(elements.getPackageOf(at));
        if (!(samePackage || codecElement.getModifiers().contains(Modifier.PUBLIC))
            || !noArgConstructorAccessible(codecElement, samePackage)) {
            error(at, "converter '" + codecName + "' needs a no-arg constructor accessible from package '"
                + elements.getPackageOf(at).getQualifiedName() + "'");
            return null;
        }
        TypeMirror wire = supertype(codec, CONVERTER).getTypeArguments().get(0);
        String subject = "converter '" + codecName + "' has wire type '" + wire + "'";
        ValueBinding inner = bindValue(at, field, wire, true, subject);
        if (inner == null) return null;
        return new ConverterBinding(inner, codecName, ConverterBinding.instanceName(codecName), typeName(type));
    }

    private static boolean noArgConstructorAccessible(TypeElement type, boolean samePackage) {
        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() == ElementKind.CONSTRUCTOR && ((ExecutableElement) member).getParameters().isEmpty()) {
                return member.getModifiers().contains(Modifier.PUBLIC)
                    || (samePackage && !member.getModifiers().contains(Modifier.PRIVATE));
            }
        }
        return false;
    }

    /** The parameterised view of {@code interfaceName} among the supertypes of {@code type}, or null. */
    private DeclaredType supertype(TypeMirror type, String interfaceName) {
        TypeElement target = elements.getTypeElement(interfaceName);
        if (target == null) return null;
        TypeMirror erasedTarget = types.erasure(target.asType());
        Deque<TypeMirror> queue = new ArrayDeque<>(List.of(type));
        while (!queue.isEmpty()) {
            TypeMirror current = queue.poll();
            if (types.isSameType(types.erasure(current), erasedTarget)) return (DeclaredType) current;
            queue.addAll(types.directSupertypes(current));
        }
        return null;
    }

    private static String typeName(TypeMirror type) {
        TypeElement element = typeElement(type);
        boolean generic = type.getKind() == TypeKind.DECLARED && !((DeclaredType) type).getTypeArguments().isEmpty();
        return element == null || generic ? type.toString() : element.getQualifiedName().toString();
    }

    /**
     * Binds one value; {@code boxed} says whether the Java type must be a reference type (explicit presence,
     * or a collection element) rather than the primitive. A registered codec fitting the component type and
     * field takes precedence over the built-in mappings.
     */
    private ValueBinding value(RecordComponentElement at, FieldDescriptor field, TypeMirror type, boolean boxed) {
        Optional<TypeMirror> registered = registeredCodecFor(at, field, type);
        if (registered == null) return null;
        if (registered.isPresent()) return custom(at, field, type, registered.get());
        return bindValue(at, field, type, boxed, "component '" + at.getSimpleName() + "' is '" + type + "'");
    }

    /** @param subject how to describe the offending Java type in an error, e.g. "component 'x' is 'int'" */
    private ValueBinding bindValue(
        RecordComponentElement at, FieldDescriptor field, TypeMirror type, boolean boxed, String subject
    ) {
        switch (field.getJavaType()) {
            case INT, LONG, FLOAT, DOUBLE, BOOLEAN -> {
                Scalar scalar = Scalar.of(field.getType());
                if (boxed ? isSame(type, scalar.boxedType()) : type.getKind() == scalar.primitive) return scalar;
                return mismatch(at, field, subject, "'" + (boxed ? scalar.boxedType() : scalar.primitiveType()) + "'");
            }
            case STRING -> {
                if (isSame(type, "java.lang.String")) return Scalar.STRING;
                return mismatch(at, field, subject, "'java.lang.String'");
            }
            case BYTE_STRING -> {
                if (isByteArray(type)) return Scalar.BYTE_ARRAY;
                if (isSame(type, "com.google.protobuf.ByteString")) return Scalar.BYTES;
                return mismatch(at, field, subject, "'byte[]' or 'com.google.protobuf.ByteString'");
            }
            case ENUM -> {
                if (boxed ? isSame(type, "java.lang.Integer") : type.getKind() == TypeKind.INT) {
                    return Scalar.ENUM_NUMBER;
                }
                String expectedEnum = field.getEnumType().getFullName();
                TypeElement enumElement = typeElement(type);
                if (enumElement != null && enumElement.getKind() == ElementKind.ENUM
                    && enumElement.getAnnotation(ProtoEnum.class) != null) {
                    EnumDescriptor bound = enumType(enumElement);
                    if (bound == null) return null;
                    if (!bound.getFullName().equals(expectedEnum)) {
                        return mismatch(at, field, subject, "an enum bound to '" + expectedEnum
                            + "' (this one is bound to '" + bound.getFullName() + "')");
                    }
                    String zero = zeroConstant(enumElement);
                    if (zero == null) {
                        error(at, "enum '" + enumElement.getQualifiedName() + "' has no constant with @Proto(0)");
                        return null;
                    }
                    return new EnumBinding(
                        enumElement.getQualifiedName().toString(), codecClass(elements, enumElement), zero
                    );
                }
                return mismatch(at, field, subject, "a @ProtoEnum enum bound to '" + expectedEnum + "', or '"
                    + (boxed ? "java.lang.Integer" : "int") + "' for the raw value number");
            }
            case MESSAGE -> {
                String expectedMessage = field.getMessageType().getFullName();
                TypeElement recordElement = typeElement(type);
                if (recordElement != null && recordElement.getAnnotation(ProtoMessage.class) != null) {
                    Descriptor bound = message(recordElement);
                    if (bound == null) return null;
                    if (!bound.getFullName().equals(expectedMessage)) {
                        return mismatch(at, field, subject, "a record bound to '" + expectedMessage
                            + "' (this one is bound to '" + bound.getFullName() + "')");
                    }
                    return new MessageBinding(
                        recordElement.getQualifiedName().toString(), codecClass(elements, recordElement), true
                    );
                }
                List<MessageBinding> wellKnown = WELL_KNOWN.getOrDefault(expectedMessage, List.of());
                for (MessageBinding candidate : wellKnown) {
                    boolean matches = candidate.javaType().equals("byte[]")
                        ? isByteArray(type)
                        : isSame(type, candidate.javaType());
                    if (matches) return candidate;
                }
                StringBuilder expected = new StringBuilder("a @ProtoMessage record bound to '" + expectedMessage + "'");
                for (MessageBinding candidate : wellKnown) {
                    expected.append(" or '").append(candidate.javaType()).append("'");
                }
                return mismatch(at, field, subject, expected.toString());
            }
            default -> throw new IllegalStateException("unsupported field type " + field.getType());
        }
    }

    private String zeroConstant(TypeElement enumElement) {
        for (Element member : enumElement.getEnclosedElements()) {
            Proto proto = member.getAnnotation(Proto.class);
            if (member.getKind() == ElementKind.ENUM_CONSTANT && proto != null && proto.value() == 0) {
                return member.getSimpleName().toString();
            }
        }
        return null;
    }

    private <T> T mismatch(RecordComponentElement at, FieldDescriptor field, TypeMirror actual, String expected) {
        return mismatch(at, field, "component '" + at.getSimpleName() + "' is '" + actual + "'", expected);
    }

    private <T> T mismatch(RecordComponentElement at, FieldDescriptor field, String subject, String expected) {
        String hint = "";
        if (field.getJavaType() != FieldDescriptor.JavaType.MESSAGE && !field.isRepeated()) {
            hint = field.hasPresence()
                ? "; the field has explicit presence, so absence must be representable"
                : "; the field has implicit presence, so null would have no wire representation";
        }
        error(at, "field '" + field.getFullName() + "' is '" + describe(field) + "' but " + subject + "; expected "
            + expected + hint);
        return null;
    }

    private static String describe(FieldDescriptor field) {
        if (field.isMapField()) {
            Descriptor entry = field.getMessageType();
            return "map<" + describe(entry.findFieldByNumber(1)) + ", " + describe(entry.findFieldByNumber(2)) + ">";
        }
        String type = switch (field.getType()) {
            case MESSAGE -> field.getMessageType().getFullName();
            case ENUM -> field.getEnumType().getFullName();
            default -> field.getType().name().toLowerCase();
        };
        if (field.isRepeated()) return "repeated " + type;
        if (field.getRealContainingOneof() != null) {
            return "oneof " + field.getRealContainingOneof().getName() + " { " + type + " }";
        }
        if (field.hasPresence() && field.getType() != FieldDescriptor.Type.MESSAGE) return "optional " + type;
        return type;
    }

    private boolean isSame(TypeMirror type, String qualifiedName) {
        TypeElement element = elements.getTypeElement(qualifiedName);
        return element != null && types.isSameType(type, element.asType());
    }

    private static boolean isByteArray(TypeMirror type) {
        return type.getKind() == TypeKind.ARRAY && ((ArrayType) type).getComponentType().getKind() == TypeKind.BYTE;
    }

    private DeclaredType declaredOf(TypeMirror type, String rawQualifiedName) {
        TypeElement raw = elements.getTypeElement(rawQualifiedName);
        if (type.getKind() != TypeKind.DECLARED || raw == null) return null;
        return types.isSameType(types.erasure(type), types.erasure(raw.asType())) ? (DeclaredType) type : null;
    }

    private static TypeElement typeElement(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return null;
        Element element = ((DeclaredType) type).asElement();
        return element instanceof TypeElement typeElement ? typeElement : null;
    }

    private void error(Element at, String message) {
        messager.printMessage(Kind.ERROR, message, at);
    }
}
