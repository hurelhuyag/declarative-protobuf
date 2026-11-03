package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.Descriptors.Descriptor;

import java.util.List;

/**
 * What the build asks to add to generated records beyond what the schema says: interfaces for them to implement
 * and annotations for them to carry. A rule names the messages it applies to by their schema full name, so the
 * same configuration holds whatever Java package the records end up in.
 *
 * @param interfaces  types written into the {@code implements} clause of every record a rule matches
 * @param annotations annotations written on every record a rule matches
 */
public record Customizations(List<Rule> interfaces, List<Rule> annotations) {

    public static final Customizations NONE = new Customizations(List.of(), List.of());

    /**
     * @param pattern a message's full name ({@code example.orders.Order}), a package or enclosing message followed
     *                by {@code .*} for everything inside it ({@code example.orders.*}), or {@code *} for every
     *                generated record
     * @param type    the fully qualified Java type to add
     */
    public record Rule(String pattern, String type) {

        /** Parses {@code <pattern>=<type>}, the value of a command-line option. */
        static Rule parse(String option, String value) {
            int equals = value.indexOf('=');
            if (equals <= 0 || equals == value.length() - 1) {
                throw new IllegalArgumentException(option + " expects <message pattern>=<java type>, got '" + value
                    + "'");
            }
            return new Rule(value.substring(0, equals), value.substring(equals + 1));
        }

        boolean matches(String fullName) {
            if (pattern.equals("*")) return true;
            if (pattern.endsWith(".*")) return fullName.startsWith(pattern.substring(0, pattern.length() - 1));
            return fullName.equals(pattern);
        }
    }

    List<String> interfacesFor(Descriptor message) {
        return matching(interfaces, message);
    }

    List<String> annotationsFor(Descriptor message) {
        return matching(annotations, message);
    }

    private static List<String> matching(List<Rule> rules, Descriptor message) {
        return rules.stream().filter(rule -> rule.matches(message.getFullName())).map(Rule::type).distinct().toList();
    }
}
