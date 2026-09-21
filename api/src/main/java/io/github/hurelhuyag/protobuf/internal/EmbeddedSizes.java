package io.github.hurelhuyag.protobuf.internal;

import java.util.Arrays;

/**
 * Scratch table carrying embedded-message sizes from a codec's size pass to its encode pass, so each nested
 * message is sized exactly once. The size pass reserves a slot when it enters an embedded message (pre-order) and
 * fills it on the way out; the encode pass, walking the same structure in the same order, reads the slots back
 * sequentially. protobuf-java gets the same effect from a {@code memoizedSize} field on every message object,
 * which immutable records cannot carry.
 * <p>
 * Not thread-safe; one instance per top-level encode.
 */
public final class EmbeddedSizes {

    private int[] sizes = new int[16];
    private int count;
    private int cursor;

    /** Size pass: reserves the slot for the embedded message about to be sized. */
    public int reserve() {
        if (count == sizes.length) sizes = Arrays.copyOf(sizes, count * 2);
        return count++;
    }

    /** Size pass: records the size of the embedded message for a reserved slot. */
    public void set(int slot, int size) {
        sizes[slot] = size;
    }

    /** Encode pass: the size of the next embedded message, in the order the size pass reserved them. */
    public int next() {
        return sizes[cursor++];
    }

    /** Prepares the table for the encode pass after the size pass has finished. */
    public EmbeddedSizes rewind() {
        cursor = 0;
        return this;
    }
}
