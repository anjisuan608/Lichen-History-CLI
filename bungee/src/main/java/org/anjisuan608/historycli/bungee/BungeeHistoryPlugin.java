package org.anjisuan608.historycli.bungee;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.PermissionNode;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BungeeCord/Waterfall 实现。
 *
 * <p>录入来源：玩家侧 {@link ChatEvent}（{@code /} 开头的输入）。
 * <b>控制台命令无法入史</b>——BungeeCord API 1.21-R0.4 已不提供 {@code CommandEvent}
 * （事件清单里只有 Chat/TabComplete/Server* 等），没有任何可在插件侧拦截控制台输入的钩子，
 * 这是平台限制而非疏漏。</p>
 *
 * <p>鉴权：BungeeCord 没有 op 概念，按 {@link PermissionNode} 子命令节点判定；
 * 非玩家发送者（控制台、其它插件）直接放行——API jar 里没有 {@code ConsoleCommandSender}
 * 类型可用，只能按「不是 ProxiedPlayer」判定。</p>
 */
public final class BungeeHistoryPlugin extends Plugin implements Listener {

    private HistoryStore store;
    /** reload 时写、命令线程读 → 必须 volatile（Bungee 的命令在调用方线程上执行）。 */
    private volatile Map<String, String> messages = new HashMap<>();
    /** 是否记录"会被转发到后端服务器"的命令（默认否）。 */
    private boolean recordForwardedCommands = false;

    @Override
    public void onEnable() {
        loadConfigAndMessages();

        File file = new File(getDataFolder(), "command_history.log");
        file.getParentFile().mkdirs();
        store = new HistoryStore(file.toPath());
        applyConfigToStore();
        store.read();

        getProxy().getPluginManager().registerCommand(this, bungeeCommand("historycliproxy", true, "historyclipro", "historyclip"));
        getProxy().getPluginManager().registerCommand(this, bungeeCommand("historyproxy", false, "historypro", "historyp"));
        getProxy().getPluginManager().registerListener(this, this);

        // 定时异步落盘：只在正常退出时写盘的话，崩溃/被 kill 会丢掉整段历史。
        // 注意 Bungee 的 schedule(delay, period, unit) 中 delay 与 period 共用同一个 unit，
        // 所以这里必须拆成「30 秒后先刷一次」+「之后每分钟刷」两条任务，
        // 否则首次落盘要等 30 分钟。
        Runnable flush = () -> {
            if (store != null && store.isDirty()) {
                store.write();
            }
        };
        getProxy().getScheduler().schedule(this, flush, 30, TimeUnit.SECONDS);
        getProxy().getScheduler().schedule(this, flush, 1, 1, TimeUnit.MINUTES);

        getLogger().info("Lichen History CLI (BungeeCord) enabled, language="
                + getConfigValue("language", "en_us"));
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.write();
            getLogger().info("Lichen History CLI (BungeeCord) history saved");
        }
    }

    /** 重新读取 config.yml 并应用（语言 + 记录开关 + 转发记录 + 历史上限）。 */
    private boolean reloadConfig() {
        if (!loadConfig()) {
            // 解析失败：保留当前配置（不能先清空再失败，那会把所有设置重置成默认值却报"重载成功"）
            return false;
        }
        try {
            loadMessages(getConfigValue("language", "en_us"));
            applyConfigToStore();
            return true;
        } catch (Exception e) {
            getLogger().warning("Failed to reload config: " + e);
            return false;
        }
    }

    private void applyConfigToStore() {
        recordForwardedCommands = Boolean.parseBoolean(getConfigValue("record_forwarded_commands", "false"));
        if (store == null) {
            return;
        }
        store.setRecordEnabled(Boolean.parseBoolean(getConfigValue("record_history", "true")));
        try {
            store.setMaxSize(Integer.parseInt(getConfigValue("history_size", "500").trim()));
        } catch (NumberFormatException e) {
            getLogger().warning("Bad history_size in config.yml, keeping " + store.maxSize());
        }
    }

    private void loadConfigAndMessages() {
        if (!loadConfig()) {
            getLogger().warning("config.yml could not be read, using defaults for this session");
        }
        loadMessages(getConfigValue("language", "en_us"));
        applyConfigToStore();
    }

    private String getConfigValue(String key, String def) {
        return configValues.getOrDefault(key, def);
    }

    private final Map<String, String> configValues = new HashMap<>();

    /**
     * 读取 config.yml。解析结果先写进临时表，成功才整体提交——
     * 解析失败时**保留**旧配置，而不是把它清空后回落到默认值。
     *
     * @return 是否成功读取
     */
    private boolean loadConfig() {
        File cfg = new File(getDataFolder(), "config.yml");
        if (!cfg.exists()) {
            getDataFolder().mkdirs();
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, cfg.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                getLogger().warning("Failed to extract config.yml: " + e);
            }
        }
        Map<String, String> parsed = new HashMap<>();
        try {
            Configuration conf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(cfg);
            for (String key : conf.getKeys()) {
                Object value = conf.get(key);
                if (value != null) {
                    parsed.put(key, String.valueOf(value));
                }
            }
        } catch (Exception e) {
            getLogger().warning("Failed to load config.yml: " + e);
            return false;
        }
        configValues.clear();
        configValues.putAll(parsed);
        return true;
    }

    private void loadMessages(String language) {
        String lang = (language == null || language.isEmpty()) ? "en_us" : language;
        Map<String, String> loaded = readLang(lang);
        if (loaded.isEmpty() && !"en_us".equals(lang)) {
            getLogger().warning("Language file '" + lang + ".json' missing or broken, falling back to en_us");
            loaded = readLang("en_us");
        }
        this.messages = loaded;
    }

    private Map<String, String> readLang(String lang) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = getResourceAsStream(lang + ".json")) {
            if (in == null) {
                return map;
            }
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            obj.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            getLogger().warning("Failed to load " + lang + ".json: " + e);
        }
        return map;
    }

    private String tr(String key, Object... args) {
        String text = messages.getOrDefault(key, key);
        if (args != null) {
            for (Object a : args) {
                text = text.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
            }
        }
        return text;
    }

    private Command bungeeCommand(String name, boolean allowFull, String... aliases) {
        return new HistoryBungeeCommand(name, allowFull, aliases);
    }

    /** 权限：非玩家发送者（控制台/插件）放行；玩家按子命令节点（含 {@code historycli.*} / {@code historycli.use}）。 */
    private boolean permitted(CommandSender sender, String[] args, boolean allowFull) {
        if (!(sender instanceof ProxiedPlayer)) {
            return true;
        }
        HistoryParser.Result result = HistoryParser.parse(args);
        HistoryParser.Action action = result == null ? HistoryParser.Action.LIST : result.action;
        if (!allowFull) {
            if (action == HistoryParser.Action.LIST_EXPLICIT || action == HistoryParser.Action.DELETE
                    || action == HistoryParser.Action.RELOAD || action == HistoryParser.Action.EXECUTE) {
                return false;
            }
        }
        return PermissionNode.isGranted(sender::hasPermission, action);
    }

    private final class HistoryBungeeCommand extends Command implements net.md_5.bungee.api.plugin.TabExecutor {

        private final boolean allowFull;

        HistoryBungeeCommand(String name, boolean allowFull, String... aliases) {
            // permission 留空：由本插件自行鉴权，才能给出可翻译的拒绝提示
            super(name, "", aliases);
            this.allowFull = allowFull;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!permitted(sender, args, allowFull)) {
                sender.sendMessage(tr(HistoryCommandHandler.Keys.NO_PERMISSION));
                return;
            }
            new BungeeHandler(BungeeHistoryPlugin.this, store, sender, allowFull).handle(args);
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (!permitted(sender, args, allowFull)) {
                return java.util.Collections.emptyList();
            }
            java.util.List<String> base = allowFull
                    ? java.util.List.of("list", "-c", "-w", "-a", "-r", "-d", "reload", "help", "?", "!!")
                    : java.util.List.of("-c", "-w", "-a", "-r", "help", "?");
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

    /**
     * 玩家侧录制：<b>只记录发给代理端的命令</b>（如 {@code /server}），
     * 默认不记录会被转发到后端服务器的命令（如 {@code /time set day}）——
     * 这由 {@code record_forwarded_commands}（默认 false）控制。
     *
     * <p>两点刻意的"不做"：</p>
     * <ul>
     *   <li><b>不在聊天里做 {@code !!} 展开</b>：代理端一律用 {@code historycliproxy !!} /
     *       {@code historyproxy}。此前的实现靠 {@code ChatEvent.setMessage} 改写输入，
     *       但 BungeeCord 1.19+ 的 {@code UpstreamBridge.handle(ClientCommand)} 会丢弃改写结果，
     *       结果是"展开没生效 + 幽灵历史行"；</li>
     *   <li><b>普通聊天文本不入史</b>（只有 {@code /} 开头的才算命令）。</li>
     * </ul>
     */
    @EventHandler
    public void onChat(ChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        String msg = event.getMessage();
        if (msg == null || msg.length() < 2 || !msg.startsWith("/")) {
            return;
        }
        String body = msg.substring(1);
        if (body.startsWith("!")) {
            // 历史展开请求：不录制、不展开、原样放行（代理端没有聊天侧展开功能）
            return;
        }
        // isExecutableCommand 只认"命令名"，不含参数；
        // getSender() 返回 Connection，只有 ProxiedPlayer 同时是 CommandSender（控制台不走这里）
        CommandSender sender = event.getSender() instanceof CommandSender cs ? cs : null;
        int space = body.indexOf(' ');
        String commandName = space > 0 ? body.substring(0, space) : body;
        boolean consumedByProxy = getProxy().getPluginManager().isExecutableCommand(commandName, sender);
        if (consumedByProxy || recordForwardedCommands) {
            store.add(body);
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
            // 展开结果若是代理端不认识的命令，dispatchCommand 返回 false（静默无操作）
            boolean dispatched = plugin.getProxy().getPluginManager().dispatchCommand(sender, command);
            if (!dispatched) {
                sendError(HistoryCommandHandler.Keys.NOT_EXECUTABLE, command);
            }
        }

        @Override
        protected boolean reloadConfig() {
            return plugin.reloadConfig();
        }
    }
}
