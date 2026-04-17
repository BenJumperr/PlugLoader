package xyz.aeroitems.plugloader.core.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class MessageBundle {

    private final Map<String, Object> values;

    private MessageBundle(Map<String, Object> values) {
        this.values = values;
    }

    public static MessageBundle load(Path messagesFile, InputStream defaultMessages) throws IOException {
        if (Files.notExists(messagesFile)) {
            Files.createDirectories(messagesFile.getParent());
            try (InputStream input = defaultMessages) {
                Files.copy(Objects.requireNonNull(input, "Missing bundled messages.yml"), messagesFile);
            }
        }

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (InputStream input = Files.newInputStream(messagesFile)) {
            Object raw = yaml.load(input);
            if (raw instanceof Map<?, ?> map) {
                return new MessageBundle(map.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()),
                                Map.Entry::getValue
                        )));
            }
        }
        return new MessageBundle(Map.of());
    }

    public String get(String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
