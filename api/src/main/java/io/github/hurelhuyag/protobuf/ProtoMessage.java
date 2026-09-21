package io.github.hurelhuyag.protobuf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record as the Java representation of a protobuf message. A {@code <RecordName>ProtoCodec} class is
 * generated next to the record at compile time.
 * <p>
 * Every record component must carry {@link Proto}. Fields of the message that the record does not declare are
 * skipped on decode and never written, so a record may be a partial projection of its message.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProtoMessage {

    /**
     * Fully qualified message name, e.g. {@code acme.orders.Order}. When empty, the message whose simple name
     * equals the record's simple name is used; it is a compile error if that is ambiguous.
     */
    String value() default "";
}
