package xyz.aeroitems.plugloader.core.service;

import xyz.aeroitems.plugloader.core.LoaderDirectories;
import xyz.aeroitems.plugloader.core.config.LoaderConfig;
import xyz.aeroitems.plugloader.core.config.LoaderConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ScaffoldService {

    private final LoaderDirectories directories;
    private final LoaderConfigManager configManager;

    public ScaffoldService(LoaderDirectories directories, LoaderConfigManager configManager) {
        this.directories = directories;
        this.configManager = configManager;
    }

    public boolean ensureExamplePlugin(LoaderConfig config) throws IOException {
        try (var stream = Files.list(directories.pluginsDirectory())) {
            if (stream.findAny().isPresent()) {
                return false;
            }
        }

        createPlugin("0", config);
        return true;
    }

    public Path createPlugin(String pluginId, LoaderConfig config) throws IOException {
        Path pluginRoot = directories.pluginDirectory(pluginId);
        String mainClass = "org.example.Main";
        Path sourceDirectory = pluginRoot.resolve("src/main/java/org/example");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(pluginRoot.resolve("src/main/resources"));

        Files.writeString(pluginRoot.resolve("plug.json"), """
id: %s
name: Example-%s
version: 1.0.0
main: %s
""".formatted(pluginId, pluginId, mainClass));

        Files.writeString(pluginRoot.resolve("pom.xml"), """
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>xyz.aeroitems.plugloader</groupId>
    <artifactId>%s</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
""".formatted(pluginId));

        Files.writeString(sourceDirectory.resolve("Main.java"), """
package org.example;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class Main {

    private Consumer<String> logger = message -> {};
    private String pluginId = "%s";
    private Path dataFolder;

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public void setDataFolder(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void onEnable() {
        logger.accept("Hello World! (%s)");
    }

    public void onDisable() {
        logger.accept("Plugin " + pluginId + " disabled.");
    }

    public boolean onCommand(String label, String[] args) {
        if (!label.equalsIgnoreCase("hello")) {
            return false;
        }
        logger.accept("Hello command received from " + pluginId + ".");
        return true;
    }
}
""".formatted(pluginId, pluginId));

        LoaderConfig.PluginConfig pluginConfig = config.plugin(pluginId);
        pluginConfig.setEnabled(true);
        if (pluginConfig.getRepositories().isEmpty()) {
            pluginConfig.setRepositories(List.of("https://repo1.maven.org/maven2/"));
        }
        configManager.save(config);

        return pluginRoot;
    }
}
