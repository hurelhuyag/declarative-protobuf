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
}
