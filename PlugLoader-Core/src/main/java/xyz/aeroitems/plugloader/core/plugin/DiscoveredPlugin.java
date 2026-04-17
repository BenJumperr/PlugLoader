package xyz.aeroitems.plugloader.core.plugin;

import java.nio.file.Path;

public record DiscoveredPlugin(
        String id,
        String name,
        String version,
        String mainClass,
        Path directory,
        Path metadataFile
) {
}
