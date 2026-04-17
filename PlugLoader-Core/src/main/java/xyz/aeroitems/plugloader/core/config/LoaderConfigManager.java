package xyz.aeroitems.plugloader.core.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LoaderConfigManager {

    private final Path configFile;
    private final Yaml parser;
    private final Yaml writer;

    public LoaderConfigManager(Path configFile) {
        this.configFile = configFile;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.parser = new Yaml(new SafeConstructor(new LoaderOptions()));
        this.writer = new Yaml(new Representer(options), options);
    }

    public LoaderConfig load(InputStream defaultConfigStream) throws IOException {
        if (Files.notExists(configFile)) {
            Files.createDirectories(configFile.getParent());
            try (InputStream input = defaultConfigStream) {
                Files.copy(Objects.requireNonNull(input, "Missing bundled config.yml"), configFile);
            }
        }

        try (InputStream input = Files.newInputStream(configFile)) {
            Object raw = parser.load(input);
            return fromMap(raw instanceof Map<?, ?> map ? castMap(map) : Map.of());
        }
    }

    public void save(LoaderConfig config) throws IOException {
        Files.createDirectories(configFile.getParent());
        try (Writer writer = Files.newBufferedWriter(configFile)) {
            this.writer.dump(toMap(config), writer);
        }
    }

    private LoaderConfig fromMap(Map<String, Object> values) {
        LoaderConfig config = new LoaderConfig();
        Map<String, Object> cacheSection = childMap(values, "cache");
        Object cacheEnabled = cacheSection.get("enabled");
        if (cacheEnabled instanceof Boolean enabled) {
            config.setCacheEnabled(enabled);
        }

        Map<String, Object> pluginsSection = childMap(values, "plugins");
        for (Map.Entry<String, Object> entry : pluginsSection.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> pluginRaw)) {
                continue;
            }
            LoaderConfig.PluginConfig pluginConfig = config.plugin(entry.getKey());
            Map<String, Object> pluginMap = castMap(pluginRaw);
            Object enabled = pluginMap.get("enabled");
            if (enabled instanceof Boolean enabledValue) {
                pluginConfig.setEnabled(enabledValue);
            }
            pluginConfig.setDependencies(stringList(pluginMap.get("dependencies")));
            pluginConfig.setRepositories(stringList(pluginMap.get("repositories")));
        }
        return config;
    }

    private Map<String, Object> toMap(LoaderConfig config) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("cache", Map.of("enabled", config.isCacheEnabled()));
        Map<String, Object> plugins = new LinkedHashMap<>();
        config.getPlugins().forEach((pluginId, pluginConfig) -> {
            Map<String, Object> pluginValues = new LinkedHashMap<>();
            pluginValues.put("enabled", pluginConfig.isEnabled());
            pluginValues.put("dependencies", new ArrayList<>(pluginConfig.getDependencies()));
            pluginValues.put("repositories", new ArrayList<>(pluginConfig.getRepositories()));
            plugins.put(pluginId, pluginValues);
        });
        values.put("plugins", plugins);
        return values;
    }

    private Map<String, Object> childMap(Map<String, Object> values, String key) {
        Object child = values.get(key);
        if (child instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return Map.of();
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object entry : list) {
            if (entry != null) {
                values.add(String.valueOf(entry));
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> values) {
        Map<String, Object> converted = new LinkedHashMap<>();
        values.forEach((key, value) -> converted.put(String.valueOf(key), value));
        return converted;
    }
}
