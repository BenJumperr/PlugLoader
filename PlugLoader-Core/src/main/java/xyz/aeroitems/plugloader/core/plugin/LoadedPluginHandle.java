package xyz.aeroitems.plugloader.core.plugin;

import java.io.Closeable;
import java.io.IOException;

public record LoadedPluginHandle(
        DiscoveredPlugin plugin,
        Object instance,
        Closeable classLoader
) {
    public void close() throws IOException {
        classLoader.close();
    }
}
