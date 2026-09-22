# declarative-protobuf — `generated-records` branch

[![CI](https://github.com/hurelhuyag/declarative-protobuf/actions/workflows/ci.yml/badge.svg)](https://github.com/hurelhuyag/declarative-protobuf/actions/workflows/ci.yml)

This branch turns the project around: instead of you declaring records and the annotation processor checking them
against the schema, **the `.proto` files are the only input** and the records are generated from them, together
with their codecs. No annotations of our own — the only marker is `@Generated`, which has `SOURCE` retention and
so does not even reach the class files.

```proto
message Order {
  Uuid id = 1;
  string customer = 2;
  Status status = 3;
  google.protobuf.Timestamp created_at = 4;
  repeated Line lines = 6;
  map<string, string> attributes = 7;
  optional int32 discount_percent = 8;
  oneof payment {
    string card_token = 9;
    string invoice_ref = 10;
  }
}
```

becomes

```java
public record Order(
    Uuid id,                          // absent = null
    String customer,
    Status status,
    Instant createdAt,                // google.protobuf.Timestamp
    List<Line> lines,                 // never null
    Map<String, String> attributes,
    Integer discountPercent,          // optional: null = absent
    Order.Payment payment             // oneof: null, or exactly one case
) {
    public sealed interface Payment {
        record CardToken(String value) implements Payment {}
        record InvoiceRef(String value) implements Payment {}
    }
}
```

and you use it through JDK types only:

```java
byte[] bytes = JavaProtobuf.encode(order);
Order back = JavaProtobuf.decode(bytes, Order.class);
JavaProtobuf.encode(outputStream, order);
Order fromStream = JavaProtobuf.decode(inputStream, Order.class);
```

## The type mapping is automatic

Because both the record and its codec are generated, there is nothing to configure: one Java type per proto type
is enough, and the choice is the lossless one in every case.

| Schema | Java |
|---|---|
| `int32` `sint32` `sfixed32` | `int` |
| `uint32` `fixed32` | `int` — two's complement, as protoc does; read with `Integer.toUnsignedLong` |
| `int64` `sint64` `sfixed64` | `long` |
| `uint64` `fixed64` | `long` — two's complement; print with `Long.toUnsignedString` |
| `float` `double` `bool` `string` | `float` `double` `boolean` `String` |
| `bytes` | `ByteBuffer`, read-only on decode, compared by content |
| `optional T` (explicit presence) | the boxed type; `null` = absent |
| message field | the generated record; `null` = absent |
| `repeated T` | `List<T>`, never null — an absent field is `List.of()` |
| `map<K, V>` | `Map<K, V>`, never null |
| enum | a generated enum plus `UNRECOGNIZED`, with `number()` / `forNumber(int)` on it |
| `oneof` | a nested **sealed interface** with one record per member |
| nested message / enum | a record or enum nested inside the outer record, as the schema nests it |
| `google.protobuf.Timestamp` / `Duration` | `Instant` / `java.time.Duration` |
| `google.protobuf.*Value` wrappers | the boxed Java type, `null` = absent |

Names follow protoc: `java_package` (or the proto package) for the package, `json_name` — lowerCamelCase — for
components, schema spelling for enum constants. A name that would collide with a keyword or with an `Object`
method gets a trailing underscore.

The two choices worth arguing about are `uint32`/`uint64` (int/long with two's complement, matching protoc and
every other Java protobuf, rather than widening) and `bytes` (`ByteBuffer` rather than `byte[]`, so that record
`equals` compares content).

## What the sealed-interface oneof buys

This is the one thing the annotation-based branch could not do safely: there, the members were separate nullable
components, so "exactly one" was a runtime check on encode. Here it is a type:

```java
if (order.payment() instanceof Order.Payment.CardToken card) charge(card.value());
```

`null` means no member is set. A member holding its type's default (`""`, `0`) is still written to the wire,
because being a case *is* the presence — the same semantics protobuf has.

## Maven wiring

`example/pom.xml` is the reference. Three steps in `generate-sources`:

1. `io.github.ascopes:protobuf-maven-plugin` runs protoc with `javaEnabled=false` and writes a descriptor set
   (`--include_imports`);
2. `exec-maven-plugin` runs `io.github.hurelhuyag.protobuf.generator.Main <descriptors.pb> <output-dir>`, with
   the generator as a plugin dependency;
3. `build-helper-maven-plugin` adds the output directory as a source root.

The generator also speaks protoc's plugin protocol: with no arguments it reads a `CodeGeneratorRequest` from
stdin and writes a `CodeGeneratorResponse` to stdout, so `protoc --java-records_out=...` works with a one-line
wrapper script and no descriptor-set file at all. `--package=<name>` overrides `java_package` for everything
generated, which is how `benchmark/` puts records and protoc's own classes side by side.

Gradle works the same way: the `com.google.protobuf` plugin for the descriptor set, a `JavaExec` task for the
generator, and `sourceSets.main.java.srcDir(...)` for the output. There is no annotation processor here, so none
of the incremental-processing caveats from the other branch apply — the generator is an ordinary task with
declared inputs and outputs.

## Runtime dependency: protobuf-javalite

The generated codecs and the `JavaProtobuf` facade use only `CodedInputStream`, `CodedOutputStream` and
`WireFormat`, all of which `protobuf-javalite` provides (1.0 MB instead of 1.8 MB). Full `protobuf-java` is needed
only by the generator, which runs at build time. Nothing in your source or on your runtime classpath mentions
`com.google.protobuf`; `example`'s `RuntimeFootprintTest` proves it with `jdeps` on every build.

If your project already has `protobuf-java` (gRPC, protoc-generated classes), exclude `protobuf-javalite` from
the api dependency — the two must not share a classpath.

## Runtime semantics

- Unknown fields are skipped; they are not preserved on re-encode.
- Repeated scalars are written packed and read in either form.
- Absent repeated and map fields decode to `List.of()` / `Map.of()`, and the canonical constructor normalises
  `null` collections to empty ones, so those components are never null.
- An enum value this build does not know decodes to `UNRECOGNIZED`; encoding it throws `IllegalArgumentException`.
- Malformed input throws `IOException`.

## Performance

`benchmark/` encodes and decodes the same `bench.Order` through the generated records and through
protoc-generated protobuf-java messages, from one schema compiled both ways.

```
mvn -pl benchmark package -DskipTests
java -jar benchmark/target/benchmarks.jar -prof gc
```

JDK 25, protobuf-java 4.35.1, i7-1355U laptop, 2 forks × 10 iterations; ns/op and allocated bytes/op:

| | small (111 B) | | large (775 B) | |
|---|---|---|---|---|
| **decode** — generated records | **266 ns** | **776 B** | **1 632 ns** | **5 840 B** |
| decode — `Order.parseFrom` | 285 ns | 1 128 B | 2 117 ns | 8 072 B |
| **encode** — generated records (size pass + write) | **189 ns** | **496 B** | **1 802 ns** | **2 688 B** |
| encode — build a message + `toByteArray()` | 325 ns | 1 016 B | 3 121 ns | 9 144 B |
| encode — `toByteArray()` again on the same message (memoized size) | 115 ns | 448 B | 1 298 ns | 2 444 B |
| build the value — record constructors | 28 ns | 392 B | 366 ns | 4 000 B |
| build the value — protobuf builders | 70 ns | 496 B | 980 ns | 6 528 B |

Read the encode rows together with the build rows: producing an event costs 28 + 189 ns with records against
325 ns with builders on the small payload, and 366 + 1 802 against 3 121 on the large one.

Decoding into records is faster and allocates less: fields land in locals and one constructor call, with no
builder, no unknown-field set and no memoized-hash slots. Encoding a freshly built value wins for the same
reason, plus a scratch table that sizes each embedded message exactly once. Re-serialising the *same* message
object is where protobuf-java wins: its `memoizedSize` field skips the size pass, and a record has nowhere to
keep one.

## proto3 coverage

Generated and tested: all 15 scalar types, messages (nested, recursive), `optional`, `repeated` (packed and
unpacked), `map`, enums (open, with `UNRECOGNIZED`), `oneof` as a sealed interface, imports, `java_package`,
`Timestamp`, `Duration` and the wrapper types.

Not supported: unknown-field preservation, `Any`, `Struct`/`Value`/`ListValue`, `FieldMask`, `Empty` (a field of
one of those is a generation error naming it), services, extensions, proto2 `required`/groups, JSON and text
format.

## What this branch gives up

The annotation branch let you bind **your own** types to the wire: a domain record with exactly the fields you
care about, `UUID` instead of a `Uuid` message, a partial projection of a large message. Here the records are
DTOs generated from the schema, so mapping to domain types is the application's job — the same trade protoc's
Java output makes, except these are immutable records with JDK types and no builders.

The `master` branch keeps the annotation-based approach.
