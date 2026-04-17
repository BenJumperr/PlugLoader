package xyz.aeroitems.plugloader.core.service;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import xyz.aeroitems.plugloader.core.LoaderDirectories;
import xyz.aeroitems.plugloader.core.LoaderLogger;
import xyz.aeroitems.plugloader.core.ServerRuntime;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MandatoryLibraryService {

    private static final String STATE_FILE_NAME = "server-state.yml";

    private final LoaderDirectories directories;
    private final LoaderLogger logger;
    private final Yaml parser;
    private final Yaml writer;

    public MandatoryLibraryService(LoaderDirectories directories, LoaderLogger logger) {
        this.directories = directories;
        this.logger = logger;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.parser = new Yaml(new SafeConstructor(new LoaderOptions()));
        this.writer = new Yaml(new Representer(options), options);
    }

    public void ensureMandatoryLibraries(ServerRuntime runtime) throws IOException {
        Files.createDirectories(directories.mandatoryDirectory());
        Path stateFile = directories.mandatoryDirectory().resolve(STATE_FILE_NAME);
        if (Files.notExists(stateFile)) {
            logger.info("Plugins in the server are loading...");
            writeState(stateFile, runtime);
            logger.info("Server plugins installed.");
            return;
        }

        Map<String, Object> previous = readState(stateFile);
        if (!isCompatible(previous, runtime)) {
            logger.warn("Incompatibility detected in installed plugins! Repairing installed plugins...");
            writeState(stateFile, runtime);
            logger.info("Repair completed successfully!");
        }
    }

    private Map<String, Object> readState(Path stateFile) throws IOException {
        try (InputStream input = Files.newInputStream(stateFile)) {
            Object raw = parser.load(input);
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> values = new LinkedHashMap<>();
                map.forEach((key, value) -> values.put(String.valueOf(key), value));
                return values;
            }
        }
        return Map.of();
    }

    private void writeState(Path stateFile, ServerRuntime runtime) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("serverName", runtime.serverName());
        values.put("serverVersion", runtime.serverVersion());
        values.put("bukkitVersion", runtime.bukkitVersion());
        values.put("plugins", List.copyOf(runtime.installedPlugins()));
        try (Writer writer = Files.newBufferedWriter(stateFile)) {
            this.writer.dump(values, writer);
        }
    }

    private boolean isCompatible(Map<String, Object> previous, ServerRuntime runtime) {
        return runtime.serverName().equals(String.valueOf(previous.get("serverName")))
                && runtime.serverVersion().equals(String.valueOf(previous.get("serverVersion")))
                && runtime.bukkitVersion().equals(String.valueOf(previous.get("bukkitVersion")));
    }
}
