package xyz.aeroitems.plugloader.core;

import java.util.List;

public record ServerRuntime(
        String serverName,
        String serverVersion,
        String bukkitVersion,
        List<String> installedPlugins
) {
}
