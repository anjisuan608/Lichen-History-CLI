package org.anjisuan608.historycli.bungee;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryStore;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class BungeeHistoryPlugin extends Plugin implements Listener {

    private HistoryStore store;
    private Map<String, String> messages = new HashMap<>();

    @Override
    public void onEnable() {
        String language = loadConfig();
        loadMessages(language);

        File file = new File(getDataFolder(), "command_history.log");
        file.getParentFile().mkdirs();
        store = new HistoryStore(file.toPath());
        store.setMaxSize(0);
        store.read();

        getProxy().getPluginManager().registerCommand(this, bungeeCommand("historycliproxy", true, "historyclipro", "historyclip"));
        getProxy().getPluginManager().registerCommand(this, bungeeCommand("historyproxy", false, "historypro", "historyp"));
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("Lichen History CLI (BungeeCord) enabled, language=" + language);
    }

    private String loadConfig() {
        File cfg = new File(getDataFolder(), "config.yml");
        String language = "en_us";
        if (!cfg.exists()) {
            getDataFolder().mkdirs();
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, cfg.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {
            }
        }
        try {
            Configuration conf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(cfg);
            language = conf.getString("language", "en_us");
        } catch (Exception ignored) {
        }
        return language;
    }

    private void loadMessages(String language) {
        String lang = (language == null || language.isEmpty()) ? "en_us" : language;
        Map<String, String> map = new HashMap<>();
        try (InputStream in = getResourceAsStream(lang + ".json")) {
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

    private Command bungeeCommand(String name, boolean allowFull, String... aliases) {
        return new HistoryBungeeCommand(name, allowFull, aliases);
    }

    private final class HistoryBungeeCommand extends Command implements net.md_5.bungee.api.plugin.TabExecutor {

        private final boolean allowFull;

        HistoryBungeeCommand(String name, boolean allowFull, String... aliases) {
            super(name, "", aliases);
            this.allowFull = allowFull;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            new BungeeHandler(BungeeHistoryPlugin.this, store, sender, allowFull).handle(args);
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            java.util.List<String> base = allowFull
                    ? java.util.List.of("list", "-c", "-w", "-a", "-r", "-d", "reload", "help", "?", "!!")
                    : java.util.List.of("list", "-c", "-w", "-a", "-r", "help", "?");
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
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        String msg = event.getMessage();
        if (msg == null || msg.isEmpty()) {
            return;
        }
        if (msg.startsWith("/")) {
            String command = msg.substring(1);
            if (!command.isEmpty() && !command.startsWith("!")) {
                store.add(command);
            }
        }
    }

    private static final class BungeeHandler extends HistoryCommandHandler {

        private final BungeeHistoryPlugin plugin;
        private final CommandSender sender;

        BungeeHandler(BungeeHistoryPlugin plugin, HistoryStore store, CommandSender sender, boolean allowFull) {
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
            plugin.getProxy().getPluginManager().dispatchCommand(sender, command);
        }
    }

    @Override
    public void onDisable() {
    }
}
