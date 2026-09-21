package io.github.hurelhuyag.protobuf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a record component to a protobuf field number, or an enum constant to a protobuf enum value number.
 * <p>
 * Everything else about the field (wire type, presence, repeated/map/oneof membership) comes from the
 * {@code .proto} schema; the annotation processor validates the declared Java type against it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
public @interface Proto {

    /** Field number (on a record component) or enum value number (on an enum constant). */
    int value();
}
