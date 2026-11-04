package io.github.hurelhuyag.protobuf.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds real consumer projects with the plugin: protoc from Maven Central, the generator and api from
 * {@code ~/.m2} (run {@code mvn install} at the repository root first).
 */
class DeclarativeProtobufPluginTest {

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** Records generated, compiled, customized and round-tripped, then up to date and from the configuration cache. */
    @Test
    void generatesRecordsAProjectCompilesAndTests(@TempDir Path project) throws IOException {
        write(project.resolve("settings.gradle.kts"), "rootProject.name = \"shop\"\n");
        write(project.resolve("build.gradle.kts"), """
            plugins {
                java
                id("io.github.hurelhuyag.declarative-protobuf")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            dependencies {
                testImplementation(platform("org.junit:junit-bom:5.12.2"))
                testImplementation("org.junit.jupiter:junit-jupiter")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            tasks.test {
                useJUnitPlatform()
            }

            declarativeProtobuf {
                implement("shop.Order", "shop.Identified")
                annotate("*", "shop.WireRecord")
                convert("shop.Uuid", "java.util.UUID", "shop.UuidConverter")
            }
            """);
        write(project.resolve("src/main/proto/shop.proto"), """
            syntax = "proto3";
            package shop;
            option java_package = "shop";

            import "google/protobuf/timestamp.proto";

            message Uuid {
              fixed64 msb = 1;
              fixed64 lsb = 2;
            }

            message Order {
              Uuid id = 1;
              string customer = 2;
              google.protobuf.Timestamp created_at = 3;
              repeated string tags = 4;
              optional int32 discount = 5;
            }
            """);
        write(project.resolve("src/main/java/shop/UuidConverter.java"), """
            package shop;

            import io.github.hurelhuyag.protobuf.ProtoConverter;
            import java.util.UUID;

            public final class UuidConverter implements ProtoConverter<ProtoUuid, UUID> {
                public UUID fromWire(ProtoUuid wire) { return new UUID(wire.msb(), wire.lsb()); }
                public ProtoUuid toWire(UUID value) {
                    return new ProtoUuid(value.getMostSignificantBits(), value.getLeastSignificantBits());
                }
            }
            """);
        write(project.resolve("src/main/java/shop/Identified.java"), """
            package shop;

            public interface Identified { java.util.UUID id(); }
            """);
        write(project.resolve("src/main/java/shop/WireRecord.java"), """
            package shop;

            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            public @interface WireRecord {}
            """);
        write(project.resolve("src/test/java/shop/OrderTest.java"), """
            package shop;

            import io.github.hurelhuyag.protobuf.JavaProtobuf;
            import java.time.Instant;
            import java.util.List;
            import java.util.UUID;
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            class OrderTest {
                @Test
                void roundTrips() throws Exception {
                    Order order = new Order(new UUID(1, 2), "ann", Instant.EPOCH, List.of("gift"), 10);
                    assertEquals(order, JavaProtobuf.decode(JavaProtobuf.encode(order), Order.class));
                    Identified identified = order;
                    assertEquals(new UUID(1, 2), identified.id());
                    assertTrue(Order.class.isAnnotationPresent(WireRecord.class));
                }
            }
            """);

        BuildResult first = GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withArguments("build", "--configuration-cache", "--stacktrace", "--warning-mode=all")
            .forwardOutput()
            .build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateRecords").getOutcome());
        assertEquals(TaskOutcome.NO_SOURCE, first.task(":generateTestRecords").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, first.task(":test").getOutcome());
        assertTrue(Files.exists(project.resolve("build/generated/sources/records/main/shop/Order.java")));
        assertTrue(Files.exists(project.resolve("build/generated/sources/records/main/shop/OrderProtoCodec.java")));
        // converted, so only the wire side of java.util.UUID, and prefixed to say so
        assertTrue(Files.exists(project.resolve("build/generated/sources/records/main/shop/ProtoUuid.java")));

        BuildResult second = GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withArguments("build", "--configuration-cache")
            .build();
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateRecords").getOutcome());
        assertTrue(second.getOutput().contains("Reusing configuration cache."), second.getOutput());
    }

    /** A customization naming no generated message is a build failure, not a silent no-op. */
    @Test
    void aCustomizationThatMatchesNothingFailsTheBuild(@TempDir Path project) throws IOException {
        write(project.resolve("settings.gradle.kts"), "rootProject.name = \"typo\"\n");
        write(project.resolve("build.gradle.kts"), """
            plugins {
                java
                id("io.github.hurelhuyag.declarative-protobuf")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            declarativeProtobuf {
                implement("typo.Pointt", "java.io.Serializable")
            }
            """);
        write(project.resolve("src/main/proto/point.proto"), """
            syntax = "proto3";
            package typo;

            message Point {
              int32 x = 1;
              int32 y = 2;
            }
            """);

        BuildResult result = GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withArguments("compileJava")
            .buildAndFail();
        assertEquals(TaskOutcome.FAILED, result.task(":generateRecords").getOutcome());
        assertTrue(
            result.getOutput().contains("'typo.Pointt' (for java.io.Serializable) matches no generated message"),
            result.getOutput()
        );
    }
}
