package xyz.aeroitems.plugloader.api;

public interface LoadedPlugin {

    default void onLoad(PluginContext context) {
    }

    default void onEnable() {
    }

    default void onDisable() {
    }

    default boolean onCommand(String label, String[] args) {
        return false;
    }
}
