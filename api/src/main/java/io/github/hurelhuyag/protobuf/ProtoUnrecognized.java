package io.github.hurelhuyag.protobuf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the enum constant that decoding yields for enum value numbers not covered by any {@link Proto} constant
 * (proto3 enums are open, so a newer writer may send values this build does not know). The constant carries no
 * {@link Proto} annotation and cannot be encoded; attempting to do so throws {@link IllegalArgumentException}.
 * <p>
 * Without such a constant, decoding an unknown value fails with
 * {@link com.google.protobuf.InvalidProtocolBufferException}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ProtoUnrecognized {
}
