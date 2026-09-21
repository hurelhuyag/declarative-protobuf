package io.github.hurelhuyag.protobuf.benchmark;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.hurelhuyag.protobuf.DeclarativeProtobuf;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Encode/decode of the same {@code bench.Order} through the generated record codec ("declarative") and through
 * protoc-generated protobuf-java messages ("protobufJava"). Run with {@code -prof gc} to see allocation per op.
 * <pre>
 * java -jar benchmark/target/benchmarks.jar -prof gc
 * </pre>
 * How to read the encode rows: a record has no place to memoize its size, so {@code encode_declarative} always
 * pays one size pass plus one write pass. protobuf-java pays the same on a freshly built message
 * ({@code encode_protobufJava} = {@code build_protobufJava} + size + write) but skips the size pass when the same
 * message object is serialised again ({@code encode_protobufJava_memoized} = write only).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class CodecBenchmark {

    /** small: 1 line, no attributes/coupons (~90 bytes). large: 20 lines, 10 attributes, 8 coupons (~1 KB). */
    @Param({"small", "large"})
    public String size;

    Order record;
    io.github.hurelhuyag.protobuf.benchmark.proto.Order message;
    byte[] bytes;
    int lines;
    int attributes;
    int coupons;

    @Setup
    public void setup() throws InvalidProtocolBufferException {
        boolean large = size.equals("large");
        lines = large ? 20 : 1;
        attributes = large ? 10 : 0;
        coupons = large ? 8 : 0;
        record = Workload.record(lines, attributes, coupons);
        message = Workload.message(lines, attributes, coupons);
        bytes = message.toByteArray();
        // both sides must agree on the wire before any number means anything
        if (!Arrays.equals(bytes, DeclarativeProtobuf.encode(record))) {
            throw new IllegalStateException("encodings differ");
        }
        if (!record.equals(DeclarativeProtobuf.decode(bytes, Order.class))) {
            throw new IllegalStateException("decode differs");
        }
        System.out.printf("%n# %s payload: %d bytes%n", size, bytes.length);
    }

    @Benchmark
    public Order build_declarative() {
        return Workload.record(lines, attributes, coupons);
    }

    @Benchmark
    public io.github.hurelhuyag.protobuf.benchmark.proto.Order build_protobufJava() {
        return Workload.message(lines, attributes, coupons);
    }

    @Benchmark
    public byte[] encode_declarative() {
        return DeclarativeProtobuf.encode(record);
    }

    @Benchmark
    public byte[] encode_protobufJava() {
        // a fresh message each time, as an application producing events would have; toByteArray on a reused
        // instance would hit protobuf-java's memoized size and measure only the write pass
        return Workload.message(lines, attributes, coupons).toByteArray();
    }

    @Benchmark
    public byte[] encode_protobufJava_memoized() {
        return message.toByteArray();
    }

    @Benchmark
    public Order decode_declarative() throws InvalidProtocolBufferException {
        return DeclarativeProtobuf.decode(bytes, Order.class);
    }

    @Benchmark
    public io.github.hurelhuyag.protobuf.benchmark.proto.Order decode_protobufJava()
        throws InvalidProtocolBufferException {
        return io.github.hurelhuyag.protobuf.benchmark.proto.Order.parseFrom(bytes);
    }
}
