package xyz.aeroitems.plugloader.core.service;

import xyz.aeroitems.plugloader.api.LoadedPlugin;
import xyz.aeroitems.plugloader.api.PluginContext;
import xyz.aeroitems.plugloader.core.LoaderDirectories;
import xyz.aeroitems.plugloader.core.LoaderLogger;
import xyz.aeroitems.plugloader.core.ServerRuntime;
import xyz.aeroitems.plugloader.core.plugin.DiscoveredPlugin;
import xyz.aeroitems.plugloader.core.plugin.LoadedPluginHandle;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RuntimePluginLoader {

    private final LoaderDirectories directories;
    private final LoaderLogger logger;
    private final ServerRuntime runtime;

    public RuntimePluginLoader(LoaderDirectories directories, LoaderLogger logger, ServerRuntime runtime) {
        this.directories = directories;
        this.logger = logger;
        this.runtime = runtime;
    }

    public LoadedPluginHandle load(DiscoveredPlugin plugin) throws Exception {
        List<URL> classpath = buildClasspath(plugin);
        URLClassLoader classLoader = new URLClassLoader(classpath.toArray(URL[]::new), getClass().getClassLoader());
        Class<?> mainClass = Class.forName(plugin.mainClass(), true, classLoader);
        Object instance = mainClass.getDeclaredConstructor().newInstance();
        Consumer<String> prefixedLogger = message -> logger.info(message);
        inject(instance, "setLogger", Consumer.class, prefixedLogger);
        inject(instance, "setPluginId", String.class, plugin.id());
        inject(instance, "setDataFolder", Path.class, plugin.directory().resolve("data"));
        inject(instance, "setServerVersion", String.class, runtime.serverVersion());

        if (instance instanceof LoadedPlugin loadedPlugin) {
            loadedPlugin.onLoad(new PluginContext(
                    plugin.id(),
                    plugin.directory(),
                    plugin.directory().resolve("data"),
                    runtime.serverName(),
                    runtime.serverVersion(),
                    prefixedLogger
            ));
        } else {
            invokeNoArgs(instance, "onLoad");
        }

        return new LoadedPluginHandle(plugin, instance, classLoader);
    }

    public void enable(LoadedPluginHandle handle) throws Exception {
        if (handle.instance() instanceof LoadedPlugin loadedPlugin) {
            loadedPlugin.onEnable();
            return;
        }
        invokeNoArgs(handle.instance(), "onEnable");
    }

    public void disable(LoadedPluginHandle handle) throws Exception {
        if (handle.instance() instanceof LoadedPlugin loadedPlugin) {
            loadedPlugin.onDisable();
            return;
        }
        invokeNoArgs(handle.instance(), "onDisable");
    }

    public boolean dispatchCommand(LoadedPluginHandle handle, String label, String[] args) {
        try {
            if (handle.instance() instanceof LoadedPlugin loadedPlugin) {
                return loadedPlugin.onCommand(label, args);
            }
            Method method = handle.instance().getClass().getMethod("onCommand", String.class, String[].class);
            Object result = method.invoke(handle.instance(), label, args);
            return result instanceof Boolean bool && bool;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Exception exception) {
            logger.error("Failed to dispatch command for plugin " + handle.plugin().id(), exception);
            return false;
        }
    }

    private List<URL> buildClasspath(DiscoveredPlugin plugin) throws IOException {
        List<URL> urls = new ArrayList<>();
        Path targetClasses = plugin.directory().resolve("target/classes");
        Path buildClasses = plugin.directory().resolve("build/classes/java/main");
        Path targetJar = plugin.directory().resolve("target").resolve(plugin.id() + ".jar");
        if (Files.exists(targetClasses)) {
            urls.add(targetClasses.toUri().toURL());
        }
        if (Files.exists(buildClasses)) {
            urls.add(buildClasses.toUri().toURL());
        }
        if (Files.exists(targetJar)) {
            urls.add(targetJar.toUri().toURL());
        }

        Path dependencyDir = directories.pluginDependenciesDirectory(plugin.id());
        if (Files.exists(dependencyDir)) {
            try (var stream = Files.list(dependencyDir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .forEach(path -> {
                            try {
                                urls.add(path.toUri().toURL());
                            } catch (Exception ignored) {
                            }
                        });
            }
        }

        if (urls.isEmpty()) {
            throw new IOException("No compiled classes or jars were found for plugin " + plugin.id());
        }
        return urls;
    }

    private void inject(Object instance, String methodName, Class<?> argumentType, Object value) {
        try {
            Method method = instance.getClass().getMethod(methodName, argumentType);
            method.invoke(instance, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void invokeNoArgs(Object instance, String methodName) throws Exception {
        try {
            Method method = instance.getClass().getMethod(methodName);
            method.invoke(instance);
        } catch (NoSuchMethodException ignored) {
        }
    }
}
