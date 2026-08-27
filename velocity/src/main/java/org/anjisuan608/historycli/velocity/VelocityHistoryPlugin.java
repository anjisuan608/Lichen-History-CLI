package org.anjisuan608.historycli.velocity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.velocity.command.HistoryProxyCommand;
import org.anjisuan608.historycli.velocity.command.HistoryClipCommand;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class VelocityHistoryPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private HistoryStore store;
    private Map<String, String> messages = new HashMap<>();

    @Inject
    public VelocityHistoryPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        Path file = dataDirectory.resolve("command_history.log");
        this.store = new HistoryStore(file);
        this.store.setMaxSize(0);
        this.store.read();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        String language = loadConfig();
        loadMessages(language);

        CommandManager manager = server.getCommandManager();
        manager.register(manager.metaBuilder("historycliproxy").aliases("historyclipro", "historyclip").build(),
                new HistoryClipCommand(store, server, messages));
        manager.register(manager.metaBuilder("historyproxy").aliases("historypro", "historyp").build(),
                new HistoryProxyCommand(store, messages));
        logger.info("Lichen History CLI (Velocity) loaded, language=" + language);
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        String cmd = event.getCommand();
        if (cmd != null && !cmd.isEmpty()) {
            store.add(cmd);
        }
    }

    private String loadConfig() {
        String language = "en_us";
        try {
            Path cfg = dataDirectory.resolve("config.properties");
            if (!Files.exists(cfg)) {
                dataDirectory.toFile().mkdirs();
                Files.writeString(cfg, "language=en_us\n", StandardCharsets.UTF_8);
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(cfg)) {
                props.load(in);
            }
            language = props.getProperty("language", "en_us");
        } catch (Exception ignored) {
        }
        return language;
    }

    private void loadMessages(String language) {
        String lang = (language == null || language.isEmpty()) ? "en_us" : language;
        Map<String, String> map = new HashMap<>();
        try (InputStream in = getClass().getResourceAsStream("/" + lang + ".json")) {
            if (in != null) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                obj.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (Exception ignored) {
        }
        this.messages = map;
    }

    public static String tr(Map<String, String> messages, String key, Object... args) {
        String text = messages.getOrDefault(key, key);
        for (Object a : args) {
            text = text.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
        }
        return text;
    }
}
