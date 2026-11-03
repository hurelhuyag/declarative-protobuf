package io.github.hurelhuyag.protobuf.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the generator on one descriptor set, in a JVM of its own so that its protobuf-java never meets the build's
 * classpath. The output directory belongs to the generator, which removes sources it did not write this time.
 */
@CacheableTask
public abstract class GenerateRecords extends DefaultTask {

    /** The descriptor set protoc wrote, imports included; absent when the source set has no {@code .proto}. */
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getDescriptorSet();

    @Classpath
    public abstract ConfigurableFileCollection getGeneratorClasspath();

    @Input
    @Optional
    public abstract Property<String> getJavaPackage();

    @Input
    public abstract ListProperty<String> getInterfaces();

    @Input
    public abstract ListProperty<String> getAnnotations();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    void generate() {
        List<String> args = new ArrayList<>();
        if (getJavaPackage().isPresent()) args.add("--package=" + getJavaPackage().get());
        for (String rule : getInterfaces().get()) args.add("--implements=" + rule);
        for (String rule : getAnnotations().get()) args.add("--annotate=" + rule);
        args.add(getDescriptorSet().getSingleFile().getAbsolutePath());
        args.add(getOutputDirectory().get().getAsFile().getAbsolutePath());
        getExecOperations().javaexec(spec -> {
            spec.classpath(getGeneratorClasspath());
            spec.getMainClass().set("io.github.hurelhuyag.protobuf.generator.Main");
            spec.args(args);
        });
    }
}
