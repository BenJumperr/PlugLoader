package xyz.aeroitems.plugloader.paper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.aeroitems.plugloader.core.LoaderLogger;
import xyz.aeroitems.plugloader.core.PlugLoaderEngine;
import xyz.aeroitems.plugloader.core.ServerRuntime;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlugLoaderPaperPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&?#([0-9A-F]{6})|(?i)&x(?:&[0-9A-F]){6}|(?i)&#([0-9A-F]{6})");

    private PlugLoaderEngine engine;
    private FileConfiguration messages;

    @Override
    public void onEnable() {
        LoaderLogger logger = new BukkitLoaderLogger(getLogger());
        this.engine = new PlugLoaderEngine(
                getDataFolder().toPath(),
                logger,
                new ServerRuntime(
                        getServer().getName(),
                        getServer().getVersion(),
                        Bukkit.getBukkitVersion(),
                        Arrays.stream(Bukkit.getPluginManager().getPlugins()).map(Plugin::getName).sorted().toList()
                ),
                () -> resource("config.yml"),
                () -> resource("messages.yml")
        );

        try {
            engine.start();
        } catch (Exception exception) {
            logger.error("PlugLoader failed to start.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        PluginCommand command = getCommand("plugloader");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String lowered = message.toLowerCase(Locale.ROOT);
        if (!lowered.startsWith("/plugloader:") && !lowered.startsWith("/plugload:")) {
            return;
        }
        int separatorIndex = message.indexOf(':');
        String payload = message.substring(separatorIndex + 1);
        String[] split = payload.split(" ");
        String label = split[0];
        String[] args = split.length > 1 ? Arrays.copyOfRange(split, 1, split.length) : new String[0];
        if (engine.forwardCommand(label, args)) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("plugloader.help")) {
            sender.sendMessage(formatWithPrefix("/plugload"));
        }

        String subCommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (subCommand) {
                case "help" -> {
                    sendHelp(sender);
                    yield true;
                }
                case "list" -> send(sender, "plugloader.list", engine.listPlugins());
                case "enable" -> send(sender, "plugloader.enable", engine.enable(args[1]));
                case "disable" -> send(sender, "plugloader.disable", engine.disable(args[1]));
                case "reload" -> {
                    if (args.length > 1) {
                        yield send(sender, "plugloader.reload", engine.reload(args[1]));
                    }
                    yield false;
                }
                case "restart" -> send(sender, "plugloader.restart", engine.restartAll());
                case "status" -> send(sender, "plugloader.status", engine.status());
                case "info" -> send(sender, "plugloader.info", engine.info(args[1]));
                case "compile" -> send(sender, "plugloader.compile", engine.compile(args[1]));
                case "cache" -> {
                    if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
                        yield send(sender, "plugloader.cache.clear", engine.clearCache());
                    }
                    yield false;
                }
                case "create" -> send(sender, "plugloader.create", engine.create(args[1]));
                default -> false;
            };
        } catch (ArrayIndexOutOfBoundsException ignored) {
            sender.sendMessage(formatWithPrefix(message("command.missing-argument", "Missing argument.")));
            return true;
        } catch (Exception exception) {
            sender.sendMessage(formatWithPrefix(message("command.error", "PlugLoader error: %message%").replace("%message%", exception.getMessage())));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("help", "list", "enable", "disable", "reload", "restart", "status", "info", "compile", "cache", "create");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cache")) {
            return List.of("clear");
        }
        return List.of();
    }

    private boolean send(CommandSender sender, String permission, String message) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(formatWithPrefix(message("command.no-permission", "You do not have permission.")));
            return true;
        }
        sender.sendMessage(formatWithPrefix(message));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        List<String> lines = messages == null ? List.of() : messages.getStringList("help.lines");
        if (lines.isEmpty()) {
            sender.sendMessage(colorize("/plugload"));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(colorize(line));
        }
    }

    private String formatWithPrefix(String message) {
        String prefix = message("prefix", "&7[&bPlugLoader&7] &r");
        return colorize(prefix + message);
    }

    private String message(String path, String fallback) {
        return messages == null ? fallback : messages.getString(path, fallback);
    }

    private String colorize(String value) {
        String parsed = value;
        Matcher matcher = HEX_PATTERN.matcher(parsed);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (hex == null) {
                String token = matcher.group();
                hex = token.replace("&x", "").replace("&", "").replace("#", "");
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(net.md_5.bungee.api.ChatColor.of("#" + hex).toString()));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private InputStream resource(String path) {
        return getResource(path);
    }
}
