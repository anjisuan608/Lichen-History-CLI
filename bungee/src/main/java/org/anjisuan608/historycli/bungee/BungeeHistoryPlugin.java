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
    private Map<String, String> messages = new HashMap<>();

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

        // 定时异步落盘：只在正常退出时写盘的话，崩溃/被 kill 会丢掉整段历史
        getProxy().getScheduler().schedule(this, () -> {
            if (store != null && store.isDirty()) {
                store.write();
            }
        }, 30, 1, TimeUnit.MINUTES);

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

    /** 重新读取 config.yml 并应用（语言 + 记录开关 + 历史上限）。 */
    private boolean reloadConfig() {
        try {
            loadConfigAndMessages();
            applyConfigToStore();
            return true;
        } catch (Exception e) {
            getLogger().warning("Failed to reload config: " + e);
            return false;
        }
    }

    private void applyConfigToStore() {
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
        loadConfig();
        loadMessages(getConfigValue("language", "en_us"));
    }

    private String getConfigValue(String key, String def) {
        return configValues.getOrDefault(key, def);
    }

    private final Map<String, String> configValues = new HashMap<>();

    private void loadConfig() {        configValues.clear();
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
        try {
            Configuration conf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(cfg);
            for (String key : conf.getKeys()) {
                Object value = conf.get(key);
                if (value != null) {
                    configValues.put(key, String.valueOf(value));
                }
            }
        } catch (Exception e) {
            getLogger().warning("Failed to load config.yml: " + e);
        }
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

    /** 玩家侧：{@code /} 开头的输入（含 {@code /!!} 展开）。普通聊天不入史。 */
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
            String expanded = store.resolve(body);
            if (expanded != null && !expanded.startsWith("!")) {
                store.add(expanded);
                event.setMessage("/" + HistoryCommandHandler.stripLeadingSlash(expanded));
            }
            // 展开失败时放行：它可能真的是一个以 ! 开头的已注册命令
            return;
        }
        store.add(body);
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
