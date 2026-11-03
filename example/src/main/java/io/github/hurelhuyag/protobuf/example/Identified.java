package io.github.hurelhuyag.protobuf.example;

import java.util.UUID;

/**
 * Hand-written, and implemented by the generated {@link Order} because the build asks for it with
 * {@code --implements}: the record's {@code id} accessor already satisfies it.
 */
public interface Identified {

    UUID id();
}
