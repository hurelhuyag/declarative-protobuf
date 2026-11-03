plugins {
    `java-gradle-plugin`
    `maven-publish`
}

// one version for the whole project: the Maven build's, read from the root pom rather than repeated here
val pom = file("../pom.xml").readText()
group = "io.github.hurelhuyag"
version = Regex("<artifactId>declarative-protobuf</artifactId>\\s*<version>([^<]+)</version>")
    .find(pom)!!.groupValues[1]
val protobufVersion = Regex("<protobuf.version>([^<]+)</protobuf.version>").find(pom)!!.groupValues[1]
val junitVersion = Regex("<junit.version>([^<]+)</junit.version>").find(pom)!!.groupValues[1]

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("declarativeProtobuf") {
            id = "io.github.hurelhuyag.declarative-protobuf"
            implementationClass = "io.github.hurelhuyag.protobuf.gradle.DeclarativeProtobufPlugin"
            displayName = "declarative-protobuf"
            description = "Generates Java records and their codecs from .proto files"
        }
    }
}

// the versions the plugin hands to consumers: its own for the generator and api, protobuf's for protoc
val pluginProperties = tasks.register<WriteProperties>("pluginProperties") {
    destinationFile = layout.buildDirectory.file("generated/plugin-properties/declarative-protobuf.properties")
    property("version", version)
    property("protobufVersion", protobufVersion)
}
sourceSets.main {
    resources.srcDir(pluginProperties.map { it.destinationFile.get().asFile.parentFile })
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    // the functional tests resolve the generator and api from ~/.m2, where `mvn install` at the root puts them
    useJUnitPlatform()
}
