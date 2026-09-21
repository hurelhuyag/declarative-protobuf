package io.github.hurelhuyag.protobuf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java enum as the representation of a protobuf enum. Every constant must carry {@link Proto} with its
 * value number, and every value of the protobuf enum must be covered by a constant. One constant may additionally
 * carry {@link ProtoUnrecognized} to receive value numbers unknown to this build of the enum.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProtoEnum {

    /**
     * Fully qualified enum name, e.g. {@code acme.orders.Status}. When empty, the enum whose simple name equals
     * the Java enum's simple name is used; it is a compile error if that is ambiguous.
     */
    String value() default "";
}
