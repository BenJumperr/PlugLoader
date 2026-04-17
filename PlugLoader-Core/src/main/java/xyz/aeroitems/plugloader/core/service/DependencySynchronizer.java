package xyz.aeroitems.plugloader.core.service;

import xyz.aeroitems.plugloader.core.LoaderDirectories;
import xyz.aeroitems.plugloader.core.LoaderLogger;
import xyz.aeroitems.plugloader.core.config.LoaderConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DependencySynchronizer {

    private final LoaderDirectories directories;
    private final LoaderLogger logger;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DependencySynchronizer(LoaderDirectories directories, LoaderLogger logger) {
        this.directories = directories;
        this.logger = logger;
    }

    public void syncPlugin(String pluginId, LoaderConfig.PluginConfig pluginConfig, boolean cacheEnabled) throws IOException {
        Path repositoriesDir = directories.pluginRepositoriesDirectory(pluginId);
        Path dependenciesDir = directories.pluginDependenciesDirectory(pluginId);
        Files.createDirectories(repositoriesDir);
        Files.createDirectories(dependenciesDir);

        syncRepositories(repositoriesDir, pluginConfig.getRepositories());
        syncDependencies(pluginId, dependenciesDir, pluginConfig.getRepositories(), pluginConfig.getDependencies(), cacheEnabled);
    }

    public void clearCache() throws IOException {
        if (Files.notExists(directories.cacheDirectory())) {
            return;
        }
        try (var stream = Files.walk(directories.cacheDirectory())) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .filter(path -> !path.equals(directories.cacheDirectory()))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    private void syncRepositories(Path repositoriesDir, List<String> repositories) throws IOException {
        Instant lastLog = Instant.EPOCH;
        int total = Math.max(1, repositories.size());
        for (int index = 0; index < repositories.size(); index++) {
            int percent = Math.round(((index + 1) * 100.0f) / total);
            if (Duration.between(lastLog, Instant.now()).toMillis() >= 1_000 || index == repositories.size() - 1) {
                logger.info("Downloading repositories " + percent + "%");
                lastLog = Instant.now();
            }
            Path target = repositoriesDir.resolve("repository-" + index + ".txt");
            Files.writeString(target, repositories.get(index));
        }
        logger.info("Repositories have been successfully loaded.");
    }

    private void syncDependencies(String pluginId, Path dependenciesDir, List<String> repositories, List<String> dependencies, boolean cacheEnabled) throws IOException {
        Instant lastLog = Instant.EPOCH;
        int total = Math.max(1, dependencies.size());
        for (int index = 0; index < dependencies.size(); index++) {
            int percent = Math.round(((index + 1) * 100.0f) / total);
            if (Duration.between(lastLog, Instant.now()).toMillis() >= 1_000 || index == dependencies.size() - 1) {
                logger.info("Downloading dependencies " + percent + "%");
                lastLog = Instant.now();
            }
            downloadDependency(pluginId, dependencies.get(index), repositories, dependenciesDir, cacheEnabled);
        }
        logger.info("Dependencies have been successfully loaded.");
    }

    private void downloadDependency(String pluginId, String coordinate, List<String> repositories, Path dependenciesDir, boolean cacheEnabled) throws IOException {
        String[] parts = coordinate.split(":");
        if (parts.length < 3) {
            throw new IOException("Invalid dependency coordinate for plugin " + pluginId + ": " + coordinate);
        }

        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 ? "-" + parts[3] : "";
        String groupPath = groupId.replace('.', '/');
        String baseName = artifactId + "-" + version + classifier;
        String relativePath = groupPath + "/" + artifactId + "/" + version + "/" + baseName + ".jar";

        Path cacheTarget = directories.cacheDirectory().resolve(relativePath);
        Path pluginTarget = dependenciesDir.resolve(baseName + ".jar");
        if (cacheEnabled && Files.exists(cacheTarget)) {
            Files.createDirectories(pluginTarget.getParent());
            Files.copy(cacheTarget, pluginTarget, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        IOException failure = null;
        for (String repository : repositories) {
            try {
                URI uri = URI.create(trimTrailingSlash(repository) + "/" + relativePath);
                HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(30)).build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    Files.createDirectories(pluginTarget.getParent());
                    try (InputStream input = response.body()) {
                        Files.copy(input, pluginTarget, StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (cacheEnabled) {
                        Files.createDirectories(cacheTarget.getParent());
                        Files.copy(pluginTarget, cacheTarget, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return;
                }
                failure = new IOException("Repository responded with status " + response.statusCode() + " for " + coordinate);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Dependency download interrupted", exception);
            } catch (Exception exception) {
                failure = new IOException("Failed to download " + coordinate + " from " + repository, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
