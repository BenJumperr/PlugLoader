package xyz.aeroitems.plugloader.core.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoaderConfig {

    private boolean cacheEnabled = true;
    private final Map<String, PluginConfig> plugins = new LinkedHashMap<>();

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public Map<String, PluginConfig> getPlugins() {
        return plugins;
    }

    public PluginConfig plugin(String pluginId) {
        return plugins.computeIfAbsent(pluginId, ignored -> new PluginConfig());
    }

    public static final class PluginConfig {
        private boolean enabled = true;
        private List<String> dependencies = List.of();
        private List<String> repositories = List.of("https://repo1.maven.org/maven2/");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<String> dependencies) {
            this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        }

        public List<String> getRepositories() {
            return repositories;
        }

        public void setRepositories(List<String> repositories) {
            this.repositories = repositories == null ? List.of() : List.copyOf(repositories);
        }
    }
}
