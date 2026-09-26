package org.anjisuan608.historycli.bukkit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Bukkit/Spigot/Paper/Purpur/Leaves/Leaf 实现（Folia 兼容：不使用调度器，只在事件/启停回调内工作）。
 *
 * <p>录入来源：控制台 {@link ServerCommandEvent} + 玩家 {@link PlayerCommandPreprocessEvent}。
 * 只监听 {@link ServerCommandEvent} 会导致<b>玩家命令完全不入史</b>
 * （该事件的 javadoc 明确写着 “called when a command is run by a non-player”）。</p>
 */
public final class BukkitHistoryPlugin extends JavaPlugin implements Listener {

    private HistoryStore store;
    /** reload 时在配置线程写、命令/事件线程读 → 必须 volatile（Folia 上跨区域线程）。 */
    private volatile Map<String, String> messages = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyConfig();

        File file = new File("local/historycli/command_history.log");
        file.getParentFile().mkdirs();
        store = new HistoryStore(file.toPath());
        applyStoreConfig();
        try {
            store.read();
        } catch (Exception e) {
            // 日志文件损坏（非原子写崩溃的产物）时用空历史继续，
            // 而不是让整个插件加载失败
            getLogger().warning("Failed to read history file, starting with an empty history: " + e);
        }

        getCommand("historycliserver").setExecutor(bukkitCommand(true));
        getCommand("historyserver").setExecutor(bukkitCommand(false));
        getCommand("historycliserver").setTabCompleter((s, c, a, args) -> suggest(s, args, true));
        getCommand("historyserver").setTabCompleter((s, c, a, args) -> suggest(s, args, false));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Lichen History CLI (Bukkit) enabled, language=" + getConfig().getString("language", "en_us"));
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.write();
            getLogger().info("Lichen History CLI (Bukkit) history saved");
        }
    }

    /** 重新读取 config.yml 并应用（语言 + 记录开关 + 历史上限）。 */
    private boolean applyConfig() {
        try {
            reloadConfig(); // JavaPlugin：重新解析 config.yml
            File cfg = new File(getDataFolder(), "config.yml");
            // JavaPlugin.reloadConfig() 会吞掉 YAML 解析错误；文件非空却一个键都没读到
            // 说明配置损坏 —— 这种情况下要如实上报失败，而不是"配置已重载"
            if (cfg.exists() && cfg.length() > 0 && getConfig().getKeys(false).isEmpty()) {
                getLogger().warning("config.yml could not be parsed, keeping current settings");
                return false;
            }
            loadMessages(getConfig().getString("language", "en_us"));
            applyStoreConfig();
            return true;
        } catch (Exception e) {
            getLogger().warning("Failed to reload config: " + e);
            return false;
        }
    }

    private void applyStoreConfig() {
        if (store == null) {
            return;
        }
        store.setRecordEnabled(getConfig().getBoolean("record_history", true));
        store.setMaxSize(getConfig().getInt("history_size", 500));
    }

    private java.util.List<String> suggest(CommandSender sender, String[] args, boolean allowFull) {
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

    private void loadMessages(String language) {
        String lang = (language == null || language.isEmpty()) ? "en_us" : language;
        Map<String, String> map = loadLang(lang);
        if (map.isEmpty() && !"en_us".equals(lang)) {
            getLogger().warning("Language file '" + lang + ".json' missing or broken, falling back to en_us");
            map = loadLang("en_us");
        }
        this.messages = map;
    }

    private Map<String, String> loadLang(String lang) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = getResource(lang + ".json")) {
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

    /**
     * 权限判定：op 直通；其余按子命令节点（含 {@code historycli.*} / {@code historycli.use}）。
     * <p>不写死 {@code isOp()}，权限插件（LuckPerms 等）才能授予访问权。</p>
     */
    private boolean permitted(CommandSender sender, String[] args, boolean allowFull) {
        if (sender.isOp()) {
            return true;
        }
        HistoryParser.Result result = HistoryParser.parse(args);
        HistoryParser.Action action = result == null ? null : result.action;
        if (!allowFull) {
            // 普通命令只允许 bash 子集，动作与完整命令一致地映射到节点
            if (action == HistoryParser.Action.LIST_EXPLICIT || action == HistoryParser.Action.DELETE
                    || action == HistoryParser.Action.RELOAD || action == HistoryParser.Action.EXECUTE) {
                return false;
            }
        }
        return PermissionNode.isGranted(sender::hasPermission, action);
    }

    private org.bukkit.command.CommandExecutor bukkitCommand(boolean allowFull) {
        return (sender, command, label, args) -> {
            if (!permitted(sender, args, allowFull)) {
                sender.sendMessage(tr(HistoryCommandHandler.Keys.NO_PERMISSION));
                return true;
            }
            new BukkitHandler(BukkitHistoryPlugin.this, store, sender, allowFull).handle(args);
            return true;
        };
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        process(event.getSender(), event.getCommand(), event::setCommand, event::setCancelled);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() < 2 || !message.startsWith("/")) {
            return;
        }
        String body = message.substring(1);
        if (body.startsWith("!")) {
            // 裸 `!!` / `!5` 与 `/historycliserver !!` 是同一件事，必须过同一道权限门，
            // 否则任何玩家（哪怕一个 historycli.* 都没有）都能凭裸输入重放历史里的命令
            if (!permitted(event.getPlayer(), new String[]{body}, true)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(tr(HistoryCommandHandler.Keys.NO_PERMISSION));
                return;
            }
        }
        String changed = process(event.getPlayer(), body, null, event::setCancelled);
        if (changed != null) {
            // 展开结果仍是命令，改写发往服务端的内容
            event.setMessage("/" + HistoryCommandHandler.stripLeadingSlash(changed));
        }
    }

    /**
     * 录入/展开。
     *
     * @param setter 可为 null（玩家路径自行改写消息）；控制台路径用于就地替换命令
     * @return 展开后的命令；未发生展开时返回 null
     */
    private String process(CommandSender sender, String command, java.util.function.Consumer<String> setter,
                           java.util.function.Consumer<Boolean> canceller) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        if (command.startsWith("!")) {
            String expanded = store.resolve(command);
            if (expanded == null || expanded.startsWith("!")) {
                // 无匹配，或历史中存的是字面量 `!!`（继续展开会无限递归）
                canceller.accept(Boolean.TRUE);
                sender.sendMessage(tr(HistoryCommandHandler.Keys.NO_MATCH, command));
                return null;
            }
            store.add(expanded);
            String cleaned = HistoryCommandHandler.stripLeadingSlash(expanded);
            if (setter != null) {
                setter.accept(cleaned);
            }
            return cleaned;
        }
        store.add(command);
        return null;
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

        @Override
        protected boolean reloadConfig() {
            return plugin.applyConfig();
        }
    }
}
