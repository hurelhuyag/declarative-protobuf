package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;

import java.util.List;

/** One component of a generated record, and everything its codec needs to know about it. */
sealed interface Field {

    /** The component name. */
    String name();

    /** The component's declared Java type. */
    String type();

    /** What the component is called in the schema, for comments. */
    String protoName();

    /** Name of the local holding the value while decoding. */
    default String local() {
        return "f_" + name();
    }

    /**
     * A singular field. With explicit presence ({@code optional}, or any message field) the type is boxed and
     * {@code null} means absent; otherwise it is the primitive and the proto3 default means absent.
     */
    record Singular(FieldDescriptor descriptor, String name, Value value, boolean presence) implements Field {

        @Override
        public String type() {
            return presence ? value.boxedType() : value.type();
        }

        @Override
        public String protoName() {
            return descriptor.getName();
        }

        int number() {
            return descriptor.getNumber();
        }

        /** Type of the decode local: the wire-side type when a converter is attached. */
        String localType() {
            Value wire = value.wire();
            return presence ? wire.boxedType() : wire.type();
        }

        /** The constructor argument built from the decode local. */
        String constructorArg() {
            if (value.wire() == value) return local();
            return presence ? local() + " == null ? null : " + value.fromWire(local()) : value.fromWire(local());
        }
    }

    record Repeated(FieldDescriptor descriptor, String name, Value element, boolean packed) implements Field {

        @Override
        public String type() {
            return "java.util.List<" + element.boxedType() + ">";
        }

        @Override
        public String protoName() {
            return descriptor.getName();
        }

        int number() {
            return descriptor.getNumber();
        }
    }

    record MapField(FieldDescriptor descriptor, String name, Value key, Value value) implements Field {

        @Override
        public String type() {
            return "java.util.Map<" + key.boxedType() + ", " + value.boxedType() + ">";
        }

        @Override
        public String protoName() {
            return descriptor.getName();
        }

        int number() {
            return descriptor.getNumber();
        }
    }

    /**
     * A oneof, as one component typed by a generated sealed interface. {@code null} means no member is set, and
     * the type system makes "at most one" unrepresentable rather than merely checked.
     */
    record Oneof(OneofDescriptor descriptor, String name, String type, List<Case> cases) implements Field {

        @Override
        public String protoName() {
            return descriptor.getName();
        }

        /** One member: a record with a single {@code value} component, implementing the sealed interface. */
        record Case(FieldDescriptor descriptor, String type, Value value) {

            int number() {
                return descriptor.getNumber();
            }
        }
    }
}
