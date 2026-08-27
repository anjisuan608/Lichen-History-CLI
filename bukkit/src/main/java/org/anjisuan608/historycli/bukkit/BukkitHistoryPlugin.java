package org.anjisuan608.historycli.bukkit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryStore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class BukkitHistoryPlugin extends JavaPlugin implements Listener {

    private HistoryStore store;
    private Map<String, String> messages = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String language = getConfig().getString("language", "en_us");
        loadMessages(language);

        File file = new File("local/historycli/command_history.log");
        file.getParentFile().mkdirs();
        store = new HistoryStore(file.toPath());
        store.setMaxSize(0);
        store.read();

        getCommand("historycliserver").setExecutor(bukkitCommand(true));
        getCommand("historyserver").setExecutor(bukkitCommand(false));
        getCommand("historycliserver").setTabCompleter((s, c, a, args) -> suggest(args));
        getCommand("historyserver").setTabCompleter((s, c, a, args) -> suggest(args));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Lichen History CLI (Bukkit) enabled, language=" + language);
    }

    private java.util.List<String> suggest(String[] args) {
        java.util.List<String> base = java.util.List.of("list", "-c", "-w", "-a", "-r", "-d", "reload", "help", "?", "!!");
        if (args.length == 0) {
            return base;
        }
        String last = args[args.length - 1];
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String b : base) {
            if (b.startsWith(last)) {
                out.add(b);
            }
        }
        return out;
    }

    private void loadMessages(String language) {
        String lang = (language == null || language.isEmpty()) ? "en_us" : language;
        Map<String, String> map = new HashMap<>();
        try (InputStream in = getResource(lang + ".json")) {
            if (in != null) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                obj.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (Exception ignored) {
        }
        this.messages = map;
    }

    private String tr(String key, Object... args) {
        String text = messages.getOrDefault(key, key);
        for (Object a : args) {
            text = text.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
        }
        return text;
    }

    private org.bukkit.command.CommandExecutor bukkitCommand(boolean allowFull) {
        return (sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage("No permission");
                return true;
            }
            new BukkitHandler(BukkitHistoryPlugin.this, store, sender, allowFull).handle(args);
            return true;
        };
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onServerCommand(ServerCommandEvent event) {
        process(event.getSender(), event.getCommand(), event::setCommand);
    }

    private void process(CommandSender sender, String command, java.util.function.Consumer<String> setter) {
        if (command == null || command.isEmpty()) {
            return;
        }
        if (command.startsWith("!")) {
            String expanded = store.resolve(command);
            if (expanded != null) {
                store.add(expanded);
                setter.accept(expanded.startsWith("/") ? expanded.substring(1) : expanded);
            }
        } else {
            store.add(command);
        }
    }

    private static final class BukkitHandler extends HistoryCommandHandler {

        private final BukkitHistoryPlugin plugin;
        private final CommandSender sender;

        BukkitHandler(BukkitHistoryPlugin plugin, HistoryStore store, CommandSender sender, boolean allowFull) {
            super(store, allowFull);
            this.plugin = plugin;
            this.sender = sender;
        }

        @Override
        protected void sendMessage(String key, Object... args) {
            sender.sendMessage(plugin.tr(key, args));
        }

        @Override
        protected void sendError(String key, Object... args) {
            sender.sendMessage(plugin.tr(key, args));
        }

        @Override
        protected void sendRow(String text) {
            sender.sendMessage(text);
        }

        @Override
        protected void executeCommand(String command) {
            Bukkit.dispatchCommand(sender, command);
        }
    }
}
