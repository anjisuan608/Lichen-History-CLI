package org.anjisuan608.historycli.velocity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.velocity.command.HistoryClipCommand;
import org.anjisuan608.historycli.velocity.command.HistoryProxyCommand;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class VelocityHistoryPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<String, String> messages = new HashMap<>();
    private HistoryStore store;
    private String language = "en_us";

    @Inject
    public VelocityHistoryPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        Path file = dataDirectory.resolve("command_history.log");
        this.store = new HistoryStore(file);
        applyConfigToStore(loadConfig());
        this.store.read();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (store != null) {
                store.write();
            }
        }, "LichenHistoryCLI-Proxy-Save"));
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadMessages(language);

        CommandManager manager = server.getCommandManager();
        manager.register(manager.metaBuilder("historycliproxy").aliases("historyclipro", "historyclip").build(),
                new HistoryClipCommand(store, server, messages, this::reloadConfig));
        manager.register(manager.metaBuilder("historyproxy").aliases("historypro", "historyp").build(),
                new HistoryProxyCommand(store, messages, this::reloadConfig));

        // 定时异步落盘：只有 JVM 正常退出时才写盘的话，崩溃/被 kill 会丢掉整段历史
        server.getScheduler().buildTask(this, () -> {
            if (store != null && store.isDirty()) {
                store.write();
            }
        }).delay(30, TimeUnit.SECONDS).repeat(1, TimeUnit.MINUTES).schedule();

        logger.info("Lichen History CLI (Velocity) loaded, language=" + language);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (store != null) {
            store.write();
            logger.info("Lichen History CLI (Velocity) history saved");
        }
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        String cmd = event.getCommand();
        if (cmd != null && !cmd.isEmpty()) {
            store.add(cmd);
        }
    }

    /** 重新读取 config.properties 并应用（语言 + 记录开关 + 历史上限）。 */
    private boolean reloadConfig() {
        try {
            applyConfigToStore(loadConfig());
            loadMessages(language);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to reload config", e);
            return false;
        }
    }

    private void applyConfigToStore(Properties props) {
        this.language = props.getProperty("language", "en_us");
        store.setRecordEnabled(Boolean.parseBoolean(props.getProperty("record_history", "true")));
        try {
            store.setMaxSize(Integer.parseInt(props.getProperty("history_size", "500").trim()));
        } catch (NumberFormatException e) {
            logger.warn("Bad history_size in config.properties, keeping {}", store.maxSize());
        }
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try {
            Path cfg = dataDirectory.resolve("config.properties");
            if (!Files.exists(cfg)) {
                dataDirectory.toFile().mkdirs();
                Files.writeString(cfg,
                        "language=en_us\n"
                                + "# 是否记录命令到本 mod 自持的历史日志\n"
                                + "record_history=true\n"
                                + "# 历史上限（条）。0 = 不限\n"
                                + "history_size=500\n",
                        StandardCharsets.UTF_8);
            }
            try (InputStream in = Files.newInputStream(cfg)) {
                props.load(in);
            }
        } catch (Exception e) {
            logger.warn("Failed to load config.properties", e);
        }
        return props;
    }

    private void loadMessages(String language) {
        String lang = (language == null || language.isEmpty()) ? "en_us" : language;
        Map<String, String> loaded = readLang(lang);
        if (loaded.isEmpty() && !"en_us".equals(lang)) {
            logger.warn("Language file '" + lang + ".json' missing or broken, falling back to en_us");
            loaded = readLang("en_us");
        }
        // 命令对象持有本 map 的引用，必须原地更新而不是替换实例
        messages.clear();
        messages.putAll(loaded);
    }

    private Map<String, String> readLang(String lang) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = getClass().getResourceAsStream("/" + lang + ".json")) {
            if (in == null) {
                return map;
            }
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            obj.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            logger.warn("Failed to load " + lang + ".json: " + e);
        }
        return map;
    }

    public static String tr(Map<String, String> messages, String key, Object... args) {
        String text = messages.getOrDefault(key, key);
        if (args != null) {
            for (Object a : args) {
                text = text.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
            }
        }
        return text;
    }
}
