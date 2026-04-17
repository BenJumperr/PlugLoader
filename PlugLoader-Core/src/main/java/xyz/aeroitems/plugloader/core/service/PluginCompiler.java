package xyz.aeroitems.plugloader.core.service;

import xyz.aeroitems.plugloader.core.LoaderLogger;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PluginCompiler {

    private final LoaderLogger logger;

    public PluginCompiler(LoaderLogger logger) {
        this.logger = logger;
    }

    public boolean compile(Path pluginDirectory) throws IOException, InterruptedException {
        if (!java.nio.file.Files.exists(pluginDirectory.resolve("pom.xml"))) {
            return false;
        }

        String executable = System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT).contains("win")
                ? "mvn.cmd"
                : "mvn";
        Process process = new ProcessBuilder(executable, "package", "-DskipTests")
                .directory(pluginDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        try (var reader = process.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info(line);
            }
        }
        return process.waitFor() == 0;
    }

    public boolean compileSourcesIfNeeded(Path pluginDirectory, String mainClass, List<Path> dependencyJars) throws IOException {
        Path sourceRoot = pluginDirectory.resolve("src/main/java");
        if (Files.notExists(sourceRoot)) {
            return false;
        }

        Path outputDirectory = pluginDirectory.resolve("target/classes");
        Path expectedClass = outputDirectory.resolve(mainClass.replace('.', '/') + ".class");
        if (Files.exists(expectedClass)) {
            return true;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            logger.warn("No system Java compiler found for " + pluginDirectory.getFileName() + ".");
            return false;
        }

        List<Path> sourceFiles;
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            sourceFiles = stream
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        }

        if (sourceFiles.isEmpty()) {
            return false;
        }

        Files.createDirectories(outputDirectory);
        List<String> options = new ArrayList<>();
        options.add("-d");
        options.add(outputDirectory.toString());
        if (!dependencyJars.isEmpty()) {
            options.add("-classpath");
            options.add(dependencyJars.stream().map(Path::toString).collect(Collectors.joining(System.getProperty("path.separator"))));
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            var compilationUnits = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            Boolean result = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
            return Boolean.TRUE.equals(result) && Files.exists(expectedClass);
        }
    }
}
