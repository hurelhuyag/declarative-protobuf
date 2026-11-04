package io.github.hurelhuyag.protobuf.generator;

import com.google.protobuf.Descriptors.Descriptor;

import java.util.List;

/**
 * Everything Java-specific the build asks for beyond what the schema says: interfaces for records to implement,
 * annotations for them to carry, and Java types of the application's own for messages and fields. The schema is a
 * contract shared with other languages, so none of this is written in it; each rule names what it applies to by its
 * schema full name, and the same configuration holds whatever Java package the records end up in.
 *
 * @param interfaces  types written into the {@code implements} clause of every record a rule matches
 * @param annotations annotations written on every record a rule matches
 * @param conversions Java types that messages or fields are seen as, through a converter
 */
public record Customizations(List<Rule> interfaces, List<Rule> annotations, List<Conversion> conversions) {

    public static final Customizations NONE = new Customizations(List.of(), List.of(), List.of());

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

    /**
     * A Java type of the application's own, mapped to and from the generated wire type by a {@code ProtoConverter}.
     *
     * @param target    the full name of a message ({@code example.orders.Uuid}), which converts every field of that
     *                  type, or of one field ({@code example.orders.Order.created_millis}), which takes precedence
     *                  over its message type's conversion and is the only way to convert a scalar
     * @param javaType  the fully qualified type the record component is declared as
     * @param converter the fully qualified {@code ProtoConverter} between the generated wire type and {@code javaType}
     */
    public record Conversion(String target, String javaType, String converter) {

        /** Parses {@code <message or field>=<java type>:<converter>}, the value of {@code --convert}. */
        static Conversion parse(String value) {
            int equals = value.indexOf('=');
            int colon = value.lastIndexOf(':');
            if (equals <= 0 || colon <= equals + 1 || colon == value.length() - 1) {
                throw new IllegalArgumentException("--convert expects <message or field>=<java type>:<converter>, got '"
                    + value + "'");
            }
            return new Conversion(
                value.substring(0, equals), value.substring(equals + 1, colon), value.substring(colon + 1)
            );
        }
    }

    Conversion conversionFor(String fullName) {
        return conversions.stream().filter(conversion -> conversion.target().equals(fullName)).findFirst().orElse(null);
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
