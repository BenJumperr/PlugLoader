package xyz.aeroitems.plugloader.paper;

import xyz.aeroitems.plugloader.core.LoaderLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

final class BukkitLoaderLogger implements LoaderLogger {

    private static final String PREFIX = "[PlugLoader] ";

    private final Logger logger;

    BukkitLoaderLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(PREFIX + message);
    }

    @Override
    public void warn(String message) {
        logger.warning(PREFIX + message);
    }

    @Override
    public void error(String message) {
        logger.severe(PREFIX + message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, PREFIX + message, throwable);
    }
}
