package xyz.aeroitems.plugloader.core;

import xyz.aeroitems.plugloader.core.config.LoaderConfig;
import xyz.aeroitems.plugloader.core.config.LoaderConfigManager;
import xyz.aeroitems.plugloader.core.config.MessageBundle;
import xyz.aeroitems.plugloader.core.plugin.DiscoveredPlugin;
import xyz.aeroitems.plugloader.core.plugin.LoadedPluginHandle;
import xyz.aeroitems.plugloader.core.plugin.PluginDescriptorReader;
import xyz.aeroitems.plugloader.core.service.DependencySynchronizer;
import xyz.aeroitems.plugloader.core.service.MandatoryLibraryService;
import xyz.aeroitems.plugloader.core.service.PluginCompiler;
import xyz.aeroitems.plugloader.core.service.RuntimePluginLoader;
import xyz.aeroitems.plugloader.core.service.ScaffoldService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class PlugLoaderEngine {

    private final LoaderDirectories directories;
    private final LoaderLogger logger;
    private final ServerRuntime runtime;
    private final Supplier<InputStream> defaultConfigSupplier;
    private final Supplier<InputStream> defaultMessagesSupplier;

    private final LoaderConfigManager configManager;
    private final PluginDescriptorReader descriptorReader = new PluginDescriptorReader();
    private final PluginCompiler pluginCompiler;
    private final Map<String, DiscoveredPlugin> discoveredPlugins = new LinkedHashMap<>();
    private final Map<String, LoadedPluginHandle> loadedPlugins = new LinkedHashMap<>();

    private LoaderConfig config;
    private MessageBundle messages;

    public PlugLoaderEngine(
            Path dataDirectory,
            LoaderLogger logger,
            ServerRuntime runtime,
            Supplier<InputStream> defaultConfigSupplier,
            Supplier<InputStream> defaultMessagesSupplier
    ) {
        this.directories = new LoaderDirectories(
                dataDirectory,
                dataDirectory.resolve("plugins"),
                dataDirectory.resolve("libs"),
                dataDirectory.resolve("libs").resolve("mandatory"),
                dataDirectory.resolve("libs").resolve("cache"),
                dataDirectory.resolve("config.yml"),
                dataDirectory.resolve("messages.yml")
        );
        this.logger = logger;
        this.runtime = runtime;
        this.defaultConfigSupplier = defaultConfigSupplier;
        this.defaultMessagesSupplier = defaultMessagesSupplier;
        this.configManager = new LoaderConfigManager(directories.configFile());
        this.pluginCompiler = new PluginCompiler(logger);
    }

    public void start() throws IOException {
        Files.createDirectories(directories.pluginsDirectory());
        Files.createDirectories(directories.libsDirectory());
        Files.createDirectories(directories.cacheDirectory());

        this.config = configManager.load(defaultConfigSupplier.get());
        this.messages = MessageBundle.load(directories.messagesFile(), defaultMessagesSupplier.get());
        new ScaffoldService(directories, configManager).ensureExamplePlugin(config);

        discoverPlugins();
        logger.info("Founded " + discoveredPlugins.size() + " plugin.");

        new MandatoryLibraryService(directories, logger).ensureMandatoryLibraries(runtime);

        int enabledCount = 0;
        int disabledCount = 0;
        for (DiscoveredPlugin plugin : discoveredPlugins.values()) {
            if (config.plugin(plugin.id()).isEnabled()) {
                enabledCount++;
            } else {
                disabledCount++;
            }
        }

        if (enabledCount > 0) {
            logger.info("Starting " + enabledCount + " plugin");
            for (DiscoveredPlugin plugin : discoveredPlugins.values()) {
                if (config.plugin(plugin.id()).isEnabled()) {
                    try {
                        startPlugin(plugin.id());
                    } catch (Exception exception) {
                        logger.error("Failed to start plugin " + plugin.id(), exception);
                    }
                }
            }
        }
        if (disabledCount > 0) {
            logger.info("It's Not Running Because " + disabledCount + " Plugins Are Disabled");
        }
    }

    public void shutdown() {
        List<LoadedPluginHandle> handles = new ArrayList<>(loadedPlugins.values());
        handles.sort(Comparator.comparing(handle -> handle.plugin().id()));
        for (LoadedPluginHandle handle : handles) {
            try {
                stopPlugin(handle.plugin().id());
            } catch (Exception exception) {
                logger.error("Failed to stop plugin " + handle.plugin().id(), exception);
            }
        }
    }

    public String listPlugins() {
        if (discoveredPlugins.isEmpty()) {
            return "No plugins found.";
        }
        StringJoiner joiner = new StringJoiner(", ");
        discoveredPlugins.values().forEach(plugin -> {
            String state = loadedPlugins.containsKey(plugin.id()) ? "enabled" : "disabled";
            joiner.add(plugin.id() + " (" + state + ")");
        });
        return joiner.toString();
    }

    public String status() {
        return "Active: " + loadedPlugins.size() + ", Passive: " + Math.max(0, discoveredPlugins.size() - loadedPlugins.size());
    }

    public String info(String pluginId) {
        DiscoveredPlugin plugin = discoveredPlugins.get(pluginId);
        if (plugin == null) {
            return "Plugin not found: " + pluginId;
        }
        return "id=" + plugin.id() + ", name=" + plugin.name() + ", version=" + plugin.version() + ", main=" + plugin.mainClass();
    }

    public String enable(String pluginId) throws Exception {
        DiscoveredPlugin plugin = requirePlugin(pluginId);
        LoaderConfig.PluginConfig pluginConfig = config.plugin(pluginId);
        pluginConfig.setEnabled(true);
        configManager.save(config);
        startPlugin(plugin.id());
        return "Plugin " + pluginId + " enabled.";
    }

    public String disable(String pluginId) throws Exception {
        requirePlugin(pluginId);
        LoaderConfig.PluginConfig pluginConfig = config.plugin(pluginId);
        pluginConfig.setEnabled(false);
        configManager.save(config);
        stopPlugin(pluginId);
        return "Plugin " + pluginId + " disabled.";
    }

    public String reload(String pluginId) throws Exception {
        requirePlugin(pluginId);
        stopPlugin(pluginId);
        startPlugin(pluginId);
        return "Plugin " + pluginId + " reloaded.";
    }

    public String restartAll() throws Exception {
        shutdown();
        discoverPlugins();
        for (DiscoveredPlugin plugin : discoveredPlugins.values()) {
            LoaderConfig.PluginConfig pluginConfig = config.plugin(plugin.id());
            if (!pluginConfig.isEnabled()) {
                continue;
            }
            new DependencySynchronizer(directories, logger).syncPlugin(plugin.id(), pluginConfig, config.isCacheEnabled());
            startPlugin(plugin.id());
        }
        return "PlugLoader restart completed.";
    }

    public String compile(String pluginId) throws Exception {
        DiscoveredPlugin plugin = requirePlugin(pluginId);
        boolean result = pluginCompiler.compile(plugin.directory());
        return result ? "Plugin " + pluginId + " compiled successfully." : "Compilation failed for " + pluginId + ".";
    }

    public String create(String pluginId) throws IOException {
        if (discoveredPlugins.containsKey(pluginId)) {
            return "Plugin already exists: " + pluginId;
        }
        Path path = new ScaffoldService(directories, configManager).createPlugin(pluginId, config);
        discoverPlugins();
        return "Plugin scaffold created at " + path;
    }

    public String clearCache() throws IOException {
        new DependencySynchronizer(directories, logger).clearCache();
        return "Cache cleared.";
    }

    public boolean forwardCommand(String label, String[] args) {
        for (LoadedPluginHandle handle : loadedPlugins.values()) {
            if (new RuntimePluginLoader(directories, logger, runtime).dispatchCommand(handle, label, args)) {
                return true;
            }
        }
        return false;
    }

    private void discoverPlugins() throws IOException {
        discoveredPlugins.clear();
        try (var stream = Files.list(directories.pluginsDirectory())) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            Optional<DiscoveredPlugin> plugin = descriptorReader.read(path);
                            plugin.ifPresent(discovered -> discoveredPlugins.put(discovered.id(), discovered));
                        } catch (IOException exception) {
                            logger.error("Failed to inspect plugin directory " + path.getFileName(), exception);
                        }
                    });
        }
    }

    private void startPlugin(String pluginId) throws Exception {
        if (loadedPlugins.containsKey(pluginId)) {
            return;
        }
        DiscoveredPlugin plugin = requirePlugin(pluginId);
        LoaderConfig.PluginConfig pluginConfig = config.plugin(plugin.id());
        new DependencySynchronizer(directories, logger).syncPlugin(plugin.id(), pluginConfig, config.isCacheEnabled());
        pluginCompiler.compileSourcesIfNeeded(plugin.directory(), plugin.mainClass(), collectDependencyJars(plugin.id()));
        RuntimePluginLoader loader = new RuntimePluginLoader(directories, logger, runtime);
        LoadedPluginHandle handle = loader.load(plugin);
        loader.enable(handle);
        loadedPlugins.put(plugin.id(), handle);
    }

    private void stopPlugin(String pluginId) throws Exception {
        LoadedPluginHandle handle = loadedPlugins.remove(pluginId);
        if (handle == null) {
            return;
        }
        RuntimePluginLoader loader = new RuntimePluginLoader(directories, logger, runtime);
        loader.disable(handle);
        handle.close();
    }

    private DiscoveredPlugin requirePlugin(String pluginId) {
        return Objects.requireNonNull(discoveredPlugins.get(pluginId), "Plugin not found: " + pluginId);
    }

    private List<Path> collectDependencyJars(String pluginId) throws IOException {
        Path dependencyDirectory = directories.pluginDependenciesDirectory(pluginId);
        if (Files.notExists(dependencyDirectory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dependencyDirectory)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .toList();
        }
    }
}
