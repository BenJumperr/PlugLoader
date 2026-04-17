package xyz.aeroitems.plugloader.api;

import java.nio.file.Path;
import java.util.function.Consumer;

public record PluginContext(
        String pluginId,
        Path pluginDirectory,
        Path dataDirectory,
        String serverName,
        String serverVersion,
        Consumer<String> logger
) {
}
