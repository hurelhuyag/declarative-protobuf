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
  optional Address shipping = 5;
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
    Uuid id,                          // required: the constructor refuses null
    String customer,
    Status status,
    Instant createdAt,                // google.protobuf.Timestamp
    Address shipping,                 // optional: null = absent
    List<Line> lines,                 // never null
    Map<String, String> attributes,
    Integer discountPercent,          // optional: null = absent
    Order.Payment payment             // oneof: null, or exactly one case
) {
    public Order {
        Objects.requireNonNull(id, "example.orders.Order.id is not optional");
        Objects.requireNonNull(customer, "example.orders.Order.customer is not optional");
        Objects.requireNonNull(status, "example.orders.Order.status is not optional");
        Objects.requireNonNull(createdAt, "example.orders.Order.created_at is not optional");
        if (lines == null) lines = List.of();
        if (attributes == null) attributes = Map.of();
    }

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
| message field | the generated record; required, the constructor refuses `null` |
| `optional` message field | the generated record; `null` = absent |
| `repeated T` | `List<T>`, never null — an absent field is `List.of()` |
| `map<K, V>` | `Map<K, V>`, never null |
| enum | a generated enum plus `UNRECOGNIZED`, with `number()` / `forNumber(int)` on it |
| `oneof` | a nested **sealed interface** with one record per member |
| nested message / enum | a record or enum nested inside the outer record, as the schema nests it |
| `google.protobuf.Timestamp` / `Duration` | `Instant` / `java.time.Duration` |
| `google.protobuf.*Value` wrappers | the boxed Java type; `null` = absent when the field is `optional` |
| a message or field the build converts | the Java type you named, through your converter — see below |

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

## Custom Java types

A generated record is a fine DTO, but sometimes the schema's shape is not the type you want to hold. The schema
is a contract shared with other languages, so it names no Java class: the build does, with a repeatable generator
argument `--convert=<message or field>=<java type>:<converter>`.

```proto
message Uuid {
  fixed64 msb = 1;
  fixed64 lsb = 2;
}

message Order {
  Uuid id = 1;                    // the record component is a java.util.UUID
  repeated Uuid related = 2;      // List<UUID>
  map<string, Uuid> owners = 3;   // Map<String, UUID>
  int64 created_millis = 4;       // java.time.Instant, through the field's own conversion below
}
```

```xml
<argument>--convert=example.orders.Uuid=java.util.UUID:com.acme.UuidConverter</argument>
<argument>--convert=example.orders.Order.created_millis=java.time.Instant:com.acme.MillisConverter</argument>
```

```java
public final class UuidConverter implements ProtoConverter<ProtoUuid, UUID> {
    public UUID fromWire(ProtoUuid wire) { return new UUID(wire.msb(), wire.lsb()); }
    public ProtoUuid toWire(UUID value) {
        return new ProtoUuid(value.getMostSignificantBits(), value.getLeastSignificantBits());
    }
}
```

Naming a **message** converts every field of that type, wherever it is used: declare it once, not per field.
Naming a **field** converts that field alone and takes precedence over its message type's conversion; it is also
the way to convert a scalar. Names are schema full names, so `--package` does not change them, and a message of
an imported file can be converted too. A name that is not in the schema, a map field or a map entry, or the same
name converted twice, fails the build.

`W` is what the generator would otherwise have used — the generated record for a message field, the boxed scalar
type for a scalar field, the JDK type for a well-known type (`Instant` for `Timestamp`) — and the generated codec
still does all the wire work, calling the converter once per value on the way in and out. The wire record is still
generated, because the converter needs it, and a converted message's record is named with a `Proto` prefix
(`ProtoUuid` above): it is only the wire side of your type, and the prefix keeps the two from being mistaken for
each other. A schema type that already has that name next to it fails the build. A conversion of a single field
leaves its message's record unprefixed, since that record is still used as itself elsewhere. Converters need a public no-arg constructor and must
be stateless; one instance is shared per codec.

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

### Interfaces and annotations from the build

Like conversions, what only your Java code cares about comes from the build, not the schema. Two repeatable
arguments, each `<message pattern>=<java type>`:

```xml
<arguments>
    <argument>--implements=example.orders.Order=com.acme.Identified</argument>
    <argument>--annotate=*=com.acme.WireRecord</argument>
    <argument>${project.build.directory}/generated/descriptors.pb</argument>
    <argument>${project.build.directory}/generated-sources/records</argument>
</arguments>
```

```java
@WireRecord
public record Order(UUID id, ...) implements Identified { ... }
```

A pattern is a message's schema full name (`example.orders.Order`), a package or enclosing message followed by
`.*` for everything inside it, or `*` for every generated record; nested records are matched like top-level ones,
and `--package` does not change the names patterns see. `--implements` takes any interface the record can
satisfy — its accessors count, so `interface Identified { UUID id(); }` needs no code — and generic ones work
(`com.acme.Keyed<java.util.UUID>`). `--annotate` takes marker annotations, which must allow `ElementType.TYPE`.
A pattern that matches no generated message fails the build rather than doing nothing. The types are compiled
with the generated sources, so they live in the consuming module or its dependencies. Both arguments are for the
descriptor-set mode only, as is `--convert`; the protoc-plugin mode takes none of them.

## Gradle

`gradle-plugin/` is a Gradle plugin that does what the three Maven steps do, for every source set:

```kotlin
plugins {
    java
    id("io.github.hurelhuyag.declarative-protobuf") version "0.1.0-SNAPSHOT"
}

declarativeProtobuf {
    javaPackage = "com.acme.wire"                           // optional: --package
    implement("example.orders.Order", "com.acme.Identified") // --implements
    annotate("*", "com.acme.WireRecord")                     // --annotate
    convert("example.orders.Uuid", "java.util.UUID", "com.acme.UuidConverter") // --convert
}
```

`.proto` files go in `src/<sourceSet>/proto`. The plugin applies `com.google.protobuf` and has protoc write only a
descriptor set (protoc's own Java classes would collide with the records by name), runs the generator on it as
`generateRecords` / `generateTestRecords` into `build/generated/sources/records/<sourceSet>`, and adds that as a
Java source directory. It adds the api jar to `implementation`, and defaults protoc to the protobuf version the api
is built against; a
`protobuf { protoc { artifact = ... } }` block overrides it. The generator runs in a JVM of its own, so its
protobuf-java never meets the build's classpath, and the task is cacheable and configuration-cache safe.

Only the current Gradle is supported — the plugin is built and tested with 9.8.2 and uses its APIs freely. The
remaining deprecation warnings (`Configuration.setVisible`) come from `com.google.protobuf` 0.10.0, not from this
plugin.

The plugin is a Gradle build of its own, next to the Maven one, and takes its version from the root `pom.xml`.
Its functional tests build real projects against the generator and api in `~/.m2`, so install those first:

```sh
mvn install
gradle-plugin/gradlew -p gradle-plugin build                 # plugin + TestKit functional tests
gradle-plugin/gradlew -p gradle-plugin publishToMavenLocal   # to try it from another build via mavenLocal()
```

A consumer resolving the plugin from `mavenLocal()` lists it in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

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
- Every singular field without `optional` is required: the canonical constructor throws `NullPointerException`
  naming the schema field (`example.orders.Order.id is not optional`) when it is given `null`, so a record that
  would be an invalid message cannot be built. Strings, bytes and enums decode to their defaults when absent, so
  only a missing message field can trip it on decode — and it does: proto3 has no `required`, so a peer that
  omits such a field (or predates it) produces bytes this side refuses. Mark the field `optional` to accept that.
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

Custom types come back through converters, but two things from the annotation branch do not: a record with only
*some* of a message's fields (a projection), and a record whose component names or shape differ from the schema's.
Here the records are generated from the schema, so a domain model that does not match it is the application's job
to map — the same trade protoc's Java output makes, except these are immutable records with JDK types, no
builders, and about a tenth of the generated code.

The `master` branch keeps the annotation-based approach.

## Releasing

Push a tag `vX.Y.Z`. `.github/workflows/release.yml` sets that version, runs the full build with its tests, signs
the artifacts and publishes the parent pom, `declarative-protobuf-api` and `declarative-protobuf-generator` to Maven
Central. It then creates a GitHub Release for the tag. `pom.xml` stays a `-SNAPSHOT` in git. The example and the
benchmark are built and tested but not published. The Gradle plugin is not released by this workflow.

The repository needs the secrets `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` (a user token from central.sonatype.com),
`GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`. A version on Central can never be replaced, so to approve each release by
hand, give the `maven-central` environment a required reviewer.
