package xyz.aeroitems.plugloader.core;

import java.nio.file.Path;

public record LoaderDirectories(
        Path root,
        Path pluginsDirectory,
        Path libsDirectory,
        Path mandatoryDirectory,
        Path cacheDirectory,
        Path configFile,
        Path messagesFile
) {
    public Path pluginLibraryDirectory(String pluginId) {
        return libsDirectory.resolve(pluginId);
    }

    public Path pluginDependenciesDirectory(String pluginId) {
        return pluginLibraryDirectory(pluginId).resolve("dependencies");
    }

    public Path pluginRepositoriesDirectory(String pluginId) {
        return pluginLibraryDirectory(pluginId).resolve("repositories");
    }

    public Path pluginDirectory(String pluginId) {
        return pluginsDirectory.resolve(pluginId);
    }
}
