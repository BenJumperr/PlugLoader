package xyz.aeroitems.plugloader.core.plugin;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public final class PluginDescriptorReader {

    public Optional<DiscoveredPlugin> read(Path pluginDirectory) throws IOException {
        Path plugJson = pluginDirectory.resolve("plug.json");
        if (Files.exists(plugJson)) {
            return readYamlLike(pluginDirectory, plugJson);
        }

        Path pluginYml = pluginDirectory.resolve("plugin.yml");
        if (Files.exists(pluginYml)) {
            return readYamlLike(pluginDirectory, pluginYml);
        }
        return Optional.empty();
    }

    private Optional<DiscoveredPlugin> readYamlLike(Path pluginDirectory, Path file) throws IOException {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (InputStream input = Files.newInputStream(file)) {
            Object raw = yaml.load(input);
            if (!(raw instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            String id = stringValue(map, "id", pluginDirectory.getFileName().toString());
            String name = stringValue(map, "name", id);
            String version = stringValue(map, "version", "1.0.0");
            String main = stringValue(map, "main", null);
            if (main == null || main.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new DiscoveredPlugin(id, name, version, main, pluginDirectory, file));
        }
    }

    private String stringValue(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
