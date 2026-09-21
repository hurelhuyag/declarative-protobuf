# declarative-protobuf

[![CI](https://github.com/hurelhuyag/declarative-protobuf/actions/workflows/ci.yml/badge.svg)](https://github.com/hurelhuyag/declarative-protobuf/actions/workflows/ci.yml)

Protobuf codecs for Java records, generated at compile time. You declare a record, bind each component to a
field number, and the annotation processor validates the declaration against your `.proto` schema and generates a
straight-line encoder/decoder built on protobuf's `CodedInputStream`/`CodedOutputStream`. The only runtime
dependency is `protobuf-javalite`, and it stays behind the API: your records and calls use JDK types only —
`ByteBuffer` for `bytes`, `Instant` for `Timestamp`, `IOException` for malformed input — never
`com.google.protobuf.*`. No descriptors, reflection or builders are involved at runtime.

```java
@ProtoMessage("acme.orders.Order")
public record Order(
    @Proto(1) long id,
    @Proto(2) String customer,
    @Proto(4) List<OrderLine> lines,
    @Proto(7) Map<String, Integer> attributes,
    @Proto(9) Status status,
    @Proto(10) Instant createdAt,          // google.protobuf.Timestamp
    @Proto(11) Integer discount,           // optional int32
    @Proto(12) UUID requestId              // your own type, via a registered ProtoConverter
) {}

@ProtoEnum("acme.orders.Status")
public enum Status { @Proto(0) UNSPECIFIED, @Proto(1) OPEN, @Proto(2) CLOSED, @ProtoUnrecognized UNRECOGNIZED }

byte[] bytes = DeclarativeProtobuf.encode(order);
Order back = DeclarativeProtobuf.decode(bytes, Order.class);
```

The schema is the single source of truth: wire types, packing, presence, map/oneof structure all come from the
descriptor set, never from annotations. Anything the schema says a Java type cannot faithfully represent is a
compile error on the offending component.

## Modules

| Artifact | Scope | Contents |
|---|---|---|
| `declarative-protobuf-api` | `compile` | `@ProtoMessage`, `@ProtoEnum`, `@Proto`, `@ProtoUnrecognized`, `ProtoConverter`, `DeclarativeProtobuf` (+ an `internal` package the generated code uses) |
| `declarative-protobuf-processor` | annotation processor path | the generator |
| `declarative-protobuf-benchmark` | — | JMH comparison against protoc-generated protobuf-java (see [Performance](#performance)) |
| `declarative-protobuf-example` | — | a complete consumer: [`example.proto`](example/src/main/resources/proto/example.proto) with every data type, the [records](example/src/main/java/io/github/hurelhuyag/protobuf/example) bound to it, the [Maven wiring](example/pom.xml), and a runnable [`Main`](example/src/main/java/io/github/hurelhuyag/protobuf/example/Main.java) |

## Maven wiring

The `example` module is the reference; in short:

1. Produce a descriptor set from your `.proto` files. `io.github.ascopes:protobuf-maven-plugin` downloads protoc
   and can write one without generating any Java:

   ```xml
   <plugin>
       <groupId>io.github.ascopes</groupId>
       <artifactId>protobuf-maven-plugin</artifactId>
       <version>5.1.8</version>
       <configuration>
           <protoc>4.35.1</protoc>
           <javaEnabled>false</javaEnabled>
           <sourceDirectories>
               <sourceDirectory>${project.basedir}/src/main/resources/proto</sourceDirectory>
           </sourceDirectories>
           <outputDescriptorFile>${project.build.directory}/generated/descriptors.pb</outputDescriptorFile>
           <outputDescriptorIncludeImports>true</outputDescriptorIncludeImports>
       </configuration>
       <executions>
           <execution>
               <goals>
                   <goal>generate</goal>
               </goals>
           </execution>
       </executions>
   </plugin>
   ```

   Or run protoc yourself and commit the `.pb` next to the `.proto` files:

   ```
   protoc --descriptor_set_out=descriptors.pb --include_imports -I src/main/resources/proto src/main/resources/proto/*.proto
   ```

   `--include_imports` is required unless the only imports are `google/protobuf/*.proto`, which the processor
   bundles.

2. Point the processor at it:

   ```xml
   <dependency>
       <groupId>io.github.hurelhuyag</groupId>
       <artifactId>declarative-protobuf-api</artifactId>
       <version>${declarative-protobuf.version}</version>
   </dependency>
   ...
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-compiler-plugin</artifactId>
       <configuration>
           <annotationProcessorPaths>
               <path>
                   <groupId>io.github.hurelhuyag</groupId>
                   <artifactId>declarative-protobuf-processor</artifactId>
                   <version>${declarative-protobuf.version}</version>
               </path>
           </annotationProcessorPaths>
           <compilerArgs>
               <arg>-Adeclarative.protobuf.descriptors=${project.build.directory}/generated/descriptors.pb</arg>
           </compilerArgs>
       </configuration>
   </plugin>
   ```

   Several descriptor sets may be given, separated by the platform path separator.

## Gradle wiring

Verified with Gradle 9.7 on JDK 25 and the `com.google.protobuf` plugin 0.9.5 (Gradle 8.x itself needs JDK ≤ 24
to run). `build.gradle.kts`:

```kotlin
plugins {
    java
    id("com.google.protobuf") version "0.9.5"
}

dependencies {
    implementation("io.github.hurelhuyag:declarative-protobuf-api:${version}")
    annotationProcessor("io.github.hurelhuyag:declarative-protobuf-processor:${version}")
}

sourceSets.main { proto.srcDir("src/main/resources/proto") }        // if the .proto files live under resources

val descriptors = layout.buildDirectory.file("generated/descriptors.pb")

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.35.1" }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins.removeIf { it.name == "java" }           // descriptor set only, no protoc Java classes
            task.generateDescriptorSet = true
            task.descriptorSetOptions.includeImports = true
            task.descriptorSetOptions.path = descriptors.get().asFile.path
        }
    }
}

tasks.compileJava {
    dependsOn("generateProto")
    inputs.file(descriptors)
    options.compilerArgs.add("-Adeclarative.protobuf.descriptors=${descriptors.get().asFile}")
    classpath += files(sourceSets.main.get().resources.srcDirs)
}
```

The last three lines each cover something Gradle does differently from Maven:

- **`inputs.file(descriptors)`** — Gradle's up-to-date check knows sources, classpath and compiler arguments; the
  `-A` value is a *path* that does not change when the schema does. Without this line a `.proto` change regenerates
  the descriptor set but leaves `compileJava UP-TO-DATE`, so the codecs go stale. With it, the change recompiles
  and any mismatch surfaces as a compile error.
- **`classpath += files(...resources.srcDirs)`** — Gradle compiles without `src/main/resources` on the classpath,
  so a `META-INF/services/io.github.hurelhuyag.protobuf.ProtoConverter` in the module being compiled is invisible
  to the processor until you add it (Maven puts `target/classes`, resources included, on the compile classpath).
  Only needed for converters declared in the same module; converters from a library are found through
  `annotationProcessor("com.acme:converters:1.0")` next to the `implementation` dependency.
- **`builtins.removeIf { it.name == "java" }`** — the plugin generates protoc's Java classes by default; nothing
  here needs them.

Incremental annotation processing: the processor does not declare itself isolating or aggregating, because it
reads the descriptor set and service files, which Gradle's incremental contracts do not track. Gradle therefore
recompiles the whole module when anything in it changes — correct, just not incremental, and what Maven does
regardless.

## Declaring records

- `@ProtoMessage("pkg.Message")` on a record; with no value the message whose simple name matches the record's is
  used (compile error if ambiguous).
- `@Proto(n)` on **every** component. A record may declare only a subset of the message's fields: undeclared
  fields are skipped on decode and never written.
- Records must be accessible from their package (nested records are fine: `Outer.Inner` gets
  `Outer_InnerProtoCodec`).
- `@ProtoEnum` on a Java enum, `@Proto(n)` on each constant; every schema value must be covered and a constant with
  `@Proto(0)` is required (proto3 guarantees one). An optional `@ProtoUnrecognized` constant receives value numbers
  unknown to this build; without one, decoding an unknown number fails and the processor warns.

## Type mapping

| Schema | Java (implicit presence) | Java (explicit presence: `optional`, oneof member) |
|---|---|---|
| `int32` `sint32` `uint32` `fixed32` `sfixed32` | `int` | `Integer` |
| `int64` `sint64` `uint64` `fixed64` `sfixed64` | `long` | `Long` |
| `float` / `double` / `bool` | `float` / `double` / `boolean` | `Float` / `Double` / `Boolean` |
| `string` | `String` | `String` |
| `bytes` | `ByteBuffer` (read-only on decode; position→limit on encode) or `byte[]` | same |
| enum | `@ProtoEnum` enum, or `int` for the raw number | same, boxed |
| message | `@ProtoMessage` record (always nullable = absent) | same |
| `repeated T` | `List<T>` (boxed element type) | — |
| `map<K, V>` | `Map<K, V>` | — |
| `google.protobuf.Timestamp` / `Duration` | `Instant` / `java.time.Duration` | same |
| `google.protobuf.*Value` wrappers | the boxed Java type (`Integer`, `String`, `ByteBuffer`/`byte[]`, …) | same |

The presence column is enforced: `optional int32` as `int` is rejected (absent and 0 would collide), and plain
`int32` as `Integer` is rejected too (`null` has no wire representation). For reference types with implicit
presence (`String`, `ByteBuffer`, `byte[]`, enums) a `null` encodes like the default value, and absent decodes to
`""` / empty / the zero constant. Absent repeated and map fields decode to `List.of()` / `Map.of()`.

Any `google.protobuf.*` message can alternatively be bound to your own `@ProtoMessage` record.

### Custom Java types

One extension point: `ProtoConverter<W, T>` maps a field's wire-side Java type `W` to your type `T`. `W` is what
the component would otherwise be declared as — the boxed scalar type from the table for a scalar or enum field,
or a `@ProtoMessage` record (or a well-known type's Java mapping such as `Instant`) for a message field; the
generated codec still does the wire work. Register the class the `ServiceLoader` way and every component of type
`T` uses it — singular, list elements, map keys and values — wherever `W` fits the field. No annotation on the
field.

```proto
message Uuid { fixed64 msb = 1; fixed64 lsb = 2; }   // 20 bytes on the wire; a string UUID is 38 plus parsing
```

```java
@ProtoMessage public record Uuid(@Proto(1) long msb, @Proto(2) long lsb) {}

public final class UuidConverter implements ProtoConverter<Uuid, UUID> {
    public UUID fromWire(Uuid wire) { return new UUID(wire.msb(), wire.lsb()); }
    public Uuid toWire(UUID value) { return new Uuid(value.getMostSignificantBits(), value.getLeastSignificantBits()); }
}

@Proto(40) UUID requestId          // bound to a `Uuid` field; no annotation names the converter
```

```
# META-INF/services/io.github.hurelhuyag.protobuf.ProtoConverter
com.acme.UuidConverter
```

The same shape works for scalars — `ProtoConverter<Long, Instant>` for an `int64` holding epoch millis,
`ProtoConverter<String, Locale>` for a language tag — with the boxed wire type as `W`.

The processor reads service files from two places: jars on the **annotation processor path**, through its own
class loader (so a converter library is listed both as a dependency and under `<annotationProcessorPaths>`, the
usual convention for compile-time plugins), and the **compile classpath** through the `Filer`, where the compiler
exposes only the first such file it finds — in a Maven build the module being compiled (`src/main/resources`).

`fromWire` sees the wire default (`""`, `0`, …) for an absent implicit-presence scalar and may return `null`; a
`null` component is not written, and neither is one whose `toWire` result is the default. Absent message fields
stay `null` without the converter being called. Because matching is by fit, `ProtoConverter<String, UUID>` and
`ProtoConverter<ByteBuffer, UUID>` can both be registered; two converters fitting the same component is a compile
error, as is a registered class that is not on the compile classpath, does not implement `ProtoConverter`, or
lacks an accessible no-arg constructor.

## Runtime semantics

- Unknown fields are skipped; they are not preserved on re-encode.
- Repeated scalars are written packed (proto3 default) and read in either form.
- oneof: members are ordinary nullable components. Decoding keeps the last one seen on the wire; encoding a value
  with more than one member non-null throws `IllegalArgumentException`.
- Map entries are written with both key and value; `null` map values are written as absent.
- Malformed input throws `IOException`. `ByteBuffer` components decode as read-only buffers over a private copy and
  compare by content, so record `equals` works; `byte[]` components compare by reference, as arrays do.
- `DeclarativeProtobuf` takes and returns `byte[]`, `ByteBuffer`, `InputStream`/`OutputStream` — what a Kafka
  `Serializer`/`Deserializer` or an HTTP body hands you.

## Performance

`benchmark/` holds a JMH suite that encodes and decodes the same `bench.Order` (scalars, an embedded `Address`,
a `Timestamp`, repeated `Line` messages with repeated tags, a `map<string,string>`, packed `repeated int64`)
through the generated record codec and through protoc-generated protobuf-java messages. Two payloads: *small*
(1 line, 111 bytes) and *large* (20 lines, 10 map entries, 8 coupons, 775 bytes).

```
mvn -pl benchmark package -DskipTests
java -jar benchmark/target/benchmarks.jar -prof gc
```

JDK 25, protobuf-java 4.35.1, i7-1355U laptop, 2 forks × 10 iterations; ns/op and allocated bytes/op:

| | small | | large | |
|---|---|---|---|---|
| **decode** — record codec | **423 ns** | **776 B** | **4 162 ns** | **5 840 B** |
| decode — `Order.parseFrom` | 724 ns | 1 128 B | 4 943 ns | 7 512 B |
| **encode** — record codec (size pass + write) | **385 ns** | **512 B** | **3 169 ns** | **2 688 B** |
| encode — `toByteArray()` on a fresh message, minus build | 523 ns | 520 B | 4 066 ns | 2 468 B |
| encode — `toByteArray()` on an already-serialised message (memoized size, write only) | 336 ns | 448 B | 2 588 ns | 2 444 B |
| build — record constructors | 51 ns | 392 B | 729 ns | 4 000 B |
| build — protobuf builders | 159 ns | 496 B | 1 775 ns | 6 536 B |

Reading it:

- Decoding into records is 15–40 % faster and allocates 20–30 % less than parsing into messages: fields land in
  locals and one constructor call, with no builder, no unknown-field set and no memoized-hash slots.
- Encoding a value that was just built (the producer case) is ~25 % faster than protobuf-java, because records are
  cheap to build and the codec sizes each embedded message exactly once via a scratch table. Re-serialising the
  *same* message object is where protobuf-java wins (its `memoizedSize` field skips the size pass); a record has
  no such field, so the codec always pays one size pass — about 20 % on top of the write.
- Both sides allocate essentially only the output array on encode.

Wire compatibility is asserted in the benchmark's setup: the two encodings must be byte-identical before any
number is recorded.

## Runtime dependency: protobuf-javalite by default

The runtime and the generated code use only `CodedInputStream`, `CodedOutputStream`, `WireFormat` and
`InvalidProtocolBufferException`, none of them visible from application code. Those classes are identical in
`protobuf-java` and `protobuf-javalite`
(javalite drops the reflection layer: `Descriptors`, `DynamicMessage`, `TextFormat`), so the api depends on
`protobuf-javalite` — 1.0 MB instead of 1.8 MB. Full `protobuf-java` is needed only on the annotation processor
path, which never reaches the runtime classpath.

`protobuf-java` is a strict superset of `protobuf-javalite`, and the two must not be on one classpath together.
If your project already has `protobuf-java` (gRPC, protoc-generated classes, `DynamicMessage`), exclude javalite:

```xml
<dependency>
    <groupId>io.github.hurelhuyag</groupId>
    <artifactId>declarative-protobuf-api</artifactId>
    <version>${declarative-protobuf.version}</version>
    <exclusions>
        <exclusion>
            <groupId>com.google.protobuf</groupId>
            <artifactId>protobuf-javalite</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

`benchmark/pom.xml` does exactly this because it also holds protoc-generated classes; `example/pom.xml` is the
plain javalite consumer.

## proto3 coverage

| Feature | Status |
|---|---|
| all 15 scalar types | ✓ |
| messages, nested types, recursive messages | ✓ |
| `optional` (explicit presence) | ✓ boxed types |
| `repeated`, packed and unpacked, `[packed = false]` | ✓ always read both; written as the schema says |
| `map<K, V>` | ✓ |
| enums, open semantics, `allow_alias` | ✓ `@ProtoUnrecognized` or bind as `int` |
| `oneof` | ✓ nullable members, mutual exclusion checked on encode |
| `reserved` numbers and names | ✓ enforced by protoc; binding to a reserved number is a compile error naming it |
| `import`, `import public`, packages | ✓ |
| `Timestamp`, `Duration`, all `*Value` wrappers | ✓ built-in Java mappings |
| `Empty`, `Any`, `Struct`/`Value`/`ListValue`, `FieldMask` | bindable as your own `@ProtoMessage` records (they are ordinary messages); no built-in Java mapping |
| unknown fields | skipped on decode, not preserved on re-encode |
| JSON / text format | not in scope (wire format only) |
| proto2 `required`, extensions, groups / editions `DELIMITED` encoding | not supported (groups are a compile error; extensions are simply unknown fields) |
| editions presence / packing / closed-enum features | resolved through protobuf-java's `Descriptors`, so they follow the schema |
