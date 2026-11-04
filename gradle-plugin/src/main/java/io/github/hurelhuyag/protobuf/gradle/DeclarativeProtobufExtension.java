package io.github.hurelhuyag.protobuf.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code declarativeProtobuf} block: the generator's command-line options, for every source set.
 * <pre>
 * declarativeProtobuf {
 *     javaPackage = "com.acme.wire"
 *     implement("example.orders.Order", "com.acme.Identified")
 *     annotate("*", "com.acme.WireRecord")
 *     convert("example.orders.Uuid", "java.util.UUID", "com.acme.UuidConverter")
 * }
 * </pre>
 */
public abstract class DeclarativeProtobufExtension {

    /** Java package for everything generated, replacing each file's {@code java_package}; unset keeps the schema's. */
    public abstract Property<String> getJavaPackage();

    /** {@code <message pattern>=<interface>} rules, as {@link #implement} adds them. */
    public abstract ListProperty<String> getInterfaces();

    /** {@code <message pattern>=<annotation>} rules, as {@link #annotate} adds them. */
    public abstract ListProperty<String> getAnnotations();

    /** {@code <message or field>=<java type>:<converter>} conversions, as {@link #convert} adds them. */
    public abstract ListProperty<String> getConversions();

    /**
     * Makes the records a pattern matches implement an interface: the generator's {@code --implements}.
     *
     * @param messagePattern a message's schema full name, a prefix ending in {@code .*}, or {@code *}
     */
    public void implement(String messagePattern, String interfaceType) {
        getInterfaces().add(messagePattern + "=" + interfaceType);
    }

    /**
     * Puts a marker annotation on the records a pattern matches: the generator's {@code --annotate}.
     *
     * @param messagePattern a message's schema full name, a prefix ending in {@code .*}, or {@code *}
     */
    public void annotate(String messagePattern, String annotationType) {
        getAnnotations().add(messagePattern + "=" + annotationType);
    }

    /**
     * Declares every field of a message type, or one field, as a Java type of the application's own, mapped to and
     * from the generated wire type by a {@code ProtoConverter}: the generator's {@code --convert}.
     *
     * @param target a message's full name ({@code example.orders.Uuid}) or a field's
     *               ({@code example.orders.Order.created_millis}), which takes precedence over its message type's
     */
    public void convert(String target, String javaType, String converter) {
        getConversions().add(target + "=" + javaType + ":" + converter);
    }
}
