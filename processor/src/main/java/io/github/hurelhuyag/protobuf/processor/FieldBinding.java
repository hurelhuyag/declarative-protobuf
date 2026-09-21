package io.github.hurelhuyag.protobuf.processor;

import com.google.protobuf.Descriptors.FieldDescriptor;

import javax.lang.model.element.RecordComponentElement;

/** One record component bound to one schema field. */
sealed interface FieldBinding {

    RecordComponentElement component();

    FieldDescriptor field();

    /** The component's Java type as it appears in the record's canonical constructor. */
    String declaredType();

    default int number() {
        return field().getNumber();
    }

    default String accessor() {
        return component().getSimpleName().toString();
    }

    /** Name of the local holding this field while decoding. */
    default String local() {
        return "f" + number();
    }

    record Singular(RecordComponentElement component, FieldDescriptor field, ValueBinding value, boolean presence)
        implements FieldBinding {

        @Override
        public String declaredType() {
            return presence ? value.boxedType() : value.primitiveType();
        }

        /** Type of the decode local: the wire-side type when a converter is attached. */
        String localType() {
            return presence ? value.wire().boxedType() : value.wire().primitiveType();
        }

        /** Constructor argument built from the decode local. */
        String constructorArg() {
            if (value.wire() == value) return local();
            return presence ? local() + " == null ? null : " + value.fromWire(local()) : value.fromWire(local());
        }
    }

    record Repeated(RecordComponentElement component, FieldDescriptor field, ValueBinding element, boolean packed)
        implements FieldBinding {

        @Override
        public String declaredType() {
            return "java.util.List<" + element.boxedType() + ">";
        }
    }

    record MapField(RecordComponentElement component, FieldDescriptor field, ValueBinding key, ValueBinding value)
        implements FieldBinding {

        @Override
        public String declaredType() {
            return "java.util.Map<" + key.boxedType() + ", " + value.boxedType() + ">";
        }
    }
}
