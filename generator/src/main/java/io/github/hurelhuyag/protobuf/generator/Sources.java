package io.github.hurelhuyag.protobuf.generator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Post-processing that makes generated sources read like hand-written ones: types from the file's own package
 * lose their prefix, the handful of JDK types the generator uses get imported, and string literals are left
 * exactly as written.
 */
final class Sources {

    /** JDK types the generator names; each becomes an import when it is used. */
    private static final Map<String, String> IMPORTABLE = new LinkedHashMap<>(Map.of(
        "java.util.List", "List",
        "java.util.Map", "Map",
        "java.util.ArrayList", "ArrayList",
        "java.util.LinkedHashMap", "LinkedHashMap",
        "java.util.Objects", "Objects",
        "java.nio.ByteBuffer", "ByteBuffer",
        "java.time.Instant", "Instant",
        "java.time.Duration", "Duration"
    ));

    private Sources() {
    }

    static String simplify(String source, String ownPackage) {
        return simplify(source, ownPackage, Map.of());
    }

    /**
     * @param extra qualified types a converter brought in, keyed by qualified name; only those whose simple name
     *              cannot collide with a type of this file are passed in
     */
    static String simplify(String source, String ownPackage, Map<String, String> extra) {
        Map<String, String> importable = new LinkedHashMap<>(IMPORTABLE);
        importable.putAll(extra);
        TreeSet<String> imports = new TreeSet<>();
        List<String> lines = new ArrayList<>(List.of(source.split("\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.stripLeading();
            boolean comment = trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
            if (comment || line.startsWith("import ") || line.startsWith("package ")) continue;
            lines.set(i, rewrite(line, ownPackage, imports, importable));
        }
        return withImports(lines, imports);
    }

    /** Rewrites the parts of a line that are not inside a string literal. */
    private static String rewrite(
        String line, String ownPackage, TreeSet<String> imports, Map<String, String> importable
    ) {
        StringBuilder out = new StringBuilder(line.length());
        StringBuilder code = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < line.length()) {
                    out.append(line.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                out.append(shorten(code.toString(), ownPackage, imports, importable));
                code.setLength(0);
                out.append(c);
                inString = true;
            } else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                // a trailing comment holds proto names; leave it exactly as written
                out.append(shorten(code.toString(), ownPackage, imports, importable)).append(line.substring(i));
                return out.toString();
            } else {
                code.append(c);
            }
        }
        return out.append(shorten(code.toString(), ownPackage, imports, importable)).toString();
    }

    private static String shorten(
        String code, String ownPackage, TreeSet<String> imports, Map<String, String> importable
    ) {
        String result = code;
        for (Map.Entry<String, String> candidate : importable.entrySet()) {
            if (result.contains(candidate.getKey())) {
                result = result.replace(candidate.getKey(), candidate.getValue());
                imports.add(candidate.getKey());
            }
        }
        result = result.replace("java.lang.", "");
        return ownPackage.isEmpty() ? result : result.replace(ownPackage + ".", "");
    }

    /** Merges the collected imports into the file's existing import block and sorts it. */
    private static String withImports(List<String> lines, TreeSet<String> imports) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).startsWith("import ")) continue;
            if (first < 0) first = i;
            last = i;
        }
        for (int i = first; i >= 0 && i <= last; i++) {
            String line = lines.get(i);
            imports.add(line.substring("import ".length(), line.length() - 1));
        }
        if (imports.isEmpty()) return String.join("\n", lines);
        List<String> importLines = imports.stream().map(name -> "import " + name + ";").toList();
        List<String> result = new ArrayList<>();
        if (first < 0) {
            // no import block yet: put one after the package declaration
            int packageLine = lines.get(0).startsWith("package ") ? 0 : -1;
            result.addAll(lines.subList(0, packageLine + 1));
            if (packageLine >= 0) result.add("");
            result.addAll(importLines);
            result.addAll(lines.subList(packageLine + 1, lines.size()));
        } else {
            result.addAll(lines.subList(0, first));
            result.addAll(importLines);
            result.addAll(lines.subList(last + 1, lines.size()));
        }
        return String.join("\n", result);
    }
}
