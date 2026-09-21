package io.github.hurelhuyag.protobuf.processor;

/** Minimal indentation-aware source buffer. */
final class SourceWriter {

    private final StringBuilder out = new StringBuilder();
    private int depth;

    SourceWriter line(String text) {
        if (!text.isEmpty()) out.append("    ".repeat(depth));
        out.append(text).append('\n');
        return this;
    }

    SourceWriter open(String text) {
        line(text);
        depth++;
        return this;
    }

    SourceWriter close(String text) {
        depth--;
        return line(text);
    }

    SourceWriter close() {
        return close("}");
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
