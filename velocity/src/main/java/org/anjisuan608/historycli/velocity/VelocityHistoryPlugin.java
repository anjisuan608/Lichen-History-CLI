package org.anjisuan608.historycli.velocity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PostCommandInvocationEvent;
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
    private HistoryStore store;
    private String language = "en_us";
    /** 是否记录"会被转发到后端服务器"的命令（默认否：代理历史只关心发给代理的命令）。 */
    private boolean recordForwardedCommands = false;

    /**
     * 翻译表。用 {@code volatile} 引用整体替换（而不是原地 clear/putAll）：
     * 命令可能在插件线程池里并发读，原地修改会让读者看到半空的 map → 把裸 key 打给玩家。
     */
    private volatile Map<String, String> messages = new HashMap<>();

    @Inject
    public VelocityHistoryPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        Path file = dataDirectory.resolve("command_history.log");
        this.store = new HistoryStore(file);
        Properties props = loadConfig();
        applyConfigToStore(props != null ? props : new Properties());
        store.read();
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
                new HistoryClipCommand(store, server, this::messages, this::reloadConfig));
        manager.register(manager.metaBuilder("historyproxy").aliases("historypro", "historyp").build(),
                new HistoryProxyCommand(store, this::messages, this::reloadConfig));

        // 定时异步落盘：只有 JVM 正常退出时才写盘的话，崩溃/被 kill 会丢掉整段历史
        server.getScheduler().buildTask(this, () -> {
            if (store != null && store.isDirty()) {
                store.write();
            }
        }).delay(30, TimeUnit.SECONDS).repeat(1, TimeUnit.MINUTES).schedule();

        logger.info("Lichen History CLI (Velocity) loaded, language={}, recordForwardedCommands={}",
                language, recordForwardedCommands);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (store != null) {
            store.write();
            logger.info("Lichen History CLI (Velocity) history saved");
        }
    }

    /**
     * 命令执行完成后的结果回执 —— 这是**权威**的录制点：
     * {@code EXECUTED}/{@code SYNTAX_ERROR}/{@code EXCEPTION} 表示代理端确实消费了这条命令，
     * {@code FORWARDED} 表示原样转发给了后端服务器。
     *
     * <p>{@code CommandExecuteEvent} 是在解析<b>之前</b>触发的，无法区分这两种情况，
     * 而我们的设计要求「默认只记录发给代理端的命令」，所以分类必须在这里做。</p>
     */
    @Subscribe
    public void onPostCommand(PostCommandInvocationEvent event) {
        if (!recordable(event.getCommand())) {
            return;
        }
        if (event.getResult() == CommandResult.FORWARDED && !recordForwardedCommands) {
            return;
        }
        store.add(event.getCommand());
    }

    /**
     * 补 {@link PostCommandInvocationEvent} 覆盖不到的两种结果（{@code executeAsync} 在
     * 事件结果为 forward/denied 时会直接返回，不再走到执行与 Post 事件）：
     * <ul>
     *   <li>其它插件把结果改成 forward → 按"转发"处理，受开关控制；</li>
     *   <li>其它插件把结果改成 denied → 命令从未执行，<b>不记录</b>。</li>
     * </ul>
     * 其余情况一律交给 Post 事件，避免重复录入。
     */
    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!recordable(event.getCommand())) {
            return;
        }
        if (event.getResult().isForwardToServer() && recordForwardedCommands) {
            store.add(event.getCommand());
        }
    }

    /** 以 `!` 开头的输入是历史展开请求而不是被执行的命令，一律不入史（与其它平台一致）。 */
    private static boolean recordable(String command) {
        return command != null && !command.isEmpty() && !command.startsWith("!");
    }

    /** 重新读取 config.properties 并应用（语言 + 记录开关 + 转发记录 + 历史上限）。 */
    private boolean reloadConfig() {
        Properties props = loadConfig();
        if (props == null) {
            return false;
        }
        applyConfigToStore(props);
        loadMessages(language);
        return true;
    }

    private void applyConfigToStore(Properties props) {
        this.language = props.getProperty("language", "en_us");
        this.recordForwardedCommands = Boolean.parseBoolean(props.getProperty("record_forwarded_commands", "false"));
        store.setRecordEnabled(Boolean.parseBoolean(props.getProperty("record_history", "true")));
        try {
            store.setMaxSize(Integer.parseInt(props.getProperty("history_size", "500").trim()));
        } catch (NumberFormatException e) {
            logger.warn("Bad history_size in config.properties, keeping {}", store.maxSize());
        }
    }

    /**
     * @return 解析出的配置；读取/解析失败返回 {@code null}，由调用方决定回落还是上报失败
     */
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
                                + "# 是否记录「会被转发到后端服务器」的命令（如 /time set day）。\n"
                                + "# 默认 false：代理历史只记录发给代理本身的命令（/server、控制台 end 等）\n"
                                + "record_forwarded_commands=false\n"
                                + "# 历史上限（条）。0 = 不限\n"
                                + "history_size=500\n",
                        StandardCharsets.UTF_8);
            }
            // 用 UTF-8 读而不是 Properties 默认的 ISO-8859-1，否则用户自己写的非 ASCII 值会被搅坏
            try (InputStream in = Files.newInputStream(cfg)) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            return props;
        } catch (Exception e) {
            logger.warn("Failed to load config.properties", e);
            return null;
        }
    }

    private Map<String, String> messages() {
        return messages;
    }

    private void loadMessages(String language) {
        String lang = (language == null || language.isEmpty()) ? "en_us" : language;
        Map<String, String> loaded = readLang(lang);
        if (loaded.isEmpty() && !"en_us".equals(lang)) {
            logger.warn("Language file '" + lang + ".json' missing or broken, falling back to en_us");
            loaded = readLang("en_us");
        }
        this.messages = loaded;   // volatile 引用整体替换：读者不会看到半空的表
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
