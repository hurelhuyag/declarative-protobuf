package io.github.hurelhuyag.protobuf.gradle;

import com.google.protobuf.gradle.GenerateProtoTask;
import com.google.protobuf.gradle.ProtobufExtension;
import com.google.protobuf.gradle.ProtobufPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The Gradle counterpart of the Maven wiring in {@code example/pom.xml}: {@code com.google.protobuf} runs protoc
 * for a descriptor set only, a {@link GenerateRecords} task per source set turns it into records and codecs, and
 * its output becomes a Java source directory of that source set. The api jar is added to {@code implementation},
 * which the generated code needs at runtime and which brings {@code declarative/options.proto} onto protoc's import
 * path.
 */
public final class DeclarativeProtobufPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        Properties versions = new Properties();
        try (InputStream in = DeclarativeProtobufPlugin.class.getResourceAsStream("/declarative-protobuf.properties")) {
            versions.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String version = versions.getProperty("version");
        DeclarativeProtobufExtension extension = project.getExtensions()
            .create("declarativeProtobuf", DeclarativeProtobufExtension.class);
        // declared here, defaulting to the generator of the plugin's own version; resolved through the classpath
        Provider<DependencyScopeConfiguration> generatorDependencies = project.getConfigurations().dependencyScope(
            "declarativeProtobufGenerator",
            configuration -> configuration.defaultDependencies(dependencies -> dependencies.add(
                project.getDependencies().create("io.github.hurelhuyag:declarative-protobuf-generator:" + version)
            ))
        );
        Provider<ResolvableConfiguration> generator = project.getConfigurations().resolvable(
            "declarativeProtobufGeneratorClasspath",
            configuration -> configuration.extendsFrom(generatorDependencies.get())
        );

        project.getPluginManager().apply(ProtobufPlugin.class);
        ProtobufExtension protobuf = project.getExtensions().getByType(ProtobufExtension.class);
        // the protoc the api was built against; a protobuf { protoc { ... } } block in the build script overrides it
        protobuf.protoc(protoc -> protoc.setArtifact("com.google.protobuf:protoc:" + versions.getProperty(
            "protobufVersion"
        )));
        protobuf.generateProtoTasks(tasks -> tasks.all().configureEach(task -> {
            task.setGenerateDescriptorSet(true);
            task.getDescriptorSetOptions().setIncludeImports(true);
            // protoc's own Java classes would collide with the records by name, and nothing here needs them
            task.builtins(builtins -> builtins.removeIf(builtin -> builtin.getName().equals("java")));
        }));

        project.getPluginManager().withPlugin("java", java -> {
            project.getDependencies().add("implementation", "io.github.hurelhuyag:declarative-protobuf-api:" + version);
            project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
                TaskProvider<GenerateRecords> records = project.getTasks().register(
                    sourceSet.getTaskName("generate", "Records"), GenerateRecords.class, task -> {
                        TaskProvider<GenerateProtoTask> proto = project.getTasks().named(
                            sourceSet.getTaskName("generate", "Proto"), GenerateProtoTask.class
                        );
                        task.setDescription("Generates records and codecs from the '" + sourceSet.getName()
                            + "' .proto files.");
                        task.dependsOn(proto);
                        task.getDescriptorSet().from(proto.map(GenerateProtoTask::getDescriptorPath));
                        task.getGeneratorClasspath().from(generator);
                        task.getJavaPackage().set(extension.getJavaPackage());
                        task.getInterfaces().set(extension.getInterfaces());
                        task.getAnnotations().set(extension.getAnnotations());
                        task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(
                            "generated/sources/records/" + sourceSet.getName()
                        ));
                    }
                );
                sourceSet.getJava().srcDir(records);
            });
        });
    }
}
