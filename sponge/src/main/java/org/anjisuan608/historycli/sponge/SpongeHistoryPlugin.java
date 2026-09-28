package org.anjisuan608.historycli.sponge;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.anjisuan608.historycli.HistoryCliConfigIO;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.PermissionNode;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandCompletion;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.command.ExecuteCommandEvent;
import org.spongepowered.api.event.lifecycle.ConstructPluginEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sponge 平台实现（由 {@code META-INF/sponge_plugins.json} 声明，loader 为 {@code java_plain}）。
 *
 * <p><b>与其它平台的差异</b>：</p>
 * <ol>
 *   <li><b>命令注册</b>：Sponge 没有 {@code plugin.yml}，命令经 {@link RegisterCommandEvent}
 *       用 {@link Command.Raw} 注册（原始参数串 → {@link HistoryParser#split}，与 Bukkit 的
 *       {@code String[]} 语义一致）；</li>
 *   <li><b>录制入口</b>：{@link ExecuteCommandEvent.Pre} 同时覆盖玩家与控制台，
 *       且可在事件里改写命令（{@code !} 展开就是靠 {@code setCommand}/{@code setArguments} 实现）；</li>
 *   <li><b>权限</b>：Sponge <b>没有 op API</b>（op 属于权限体系，Sponge 靠权限节点放行原版命令，
 *       因此 op 玩家天然拥有被授权的节点）。这里沿用 Bukkit 的语义：
 *       <b>非玩家发送者</b>（控制台/命令方块）一律放行，玩家按 {@link PermissionNode} 判定；</li>
 *   <li><b>定时落盘</b>：{@code Sponge.asyncScheduler()} 每分钟异步写盘（与 paper 版一致），
 *       提交失败只告警、不影响命令功能。</li>
 * </ol>
 *
 * <p><b>配置</b>：{@code config/lichenhistorycli/lichen-history-cli.json}（与 Fabric 同一套
 * {@link HistoryCliConfigIO} 结构，取 {@code server} 分节；根级 {@code language} 可选，默认 {@code en_us}）。
 * <b>历史</b>：{@code local/historycli/command_history.log}（与 Bukkit 版同一路径）。</p>
 */
@Plugin("lichenhistorycli")
public final class SpongeHistoryPlugin {

    /** 主命令与全部别名；用于防御性剔除实现可能混入 input 的命令名。 */
    private static final List<String> NAMES = List.of(
            "historycliserver", "historyclis", "historycliser",
            "historyserver", "historys", "historyser");

    private static final Logger LOG = Logger.getLogger("LichenHistoryCli");

    private PluginContainer container;
    private Path configFile;
    private HistoryStore store;
    private ScheduledTask flushTask;
    private volatile Map<String, String> messages = new HashMap<>();

    // ---------------------------------------------------------------- 生命周期

    /** 插件构造期：建配置、加载语言、初始化存储、启动定时落盘。 */
    @Listener
    public void onConstruct(ConstructPluginEvent event) {
        this.container = event.plugin();
        try {
            this.configFile = Sponge.configManager().pluginConfig(container)
                    .directory().resolve("lichen-history-cli.json");
        } catch (Throwable t) {
            // 配置目录 API 在个别实现上可能不可用，退回 Sponge 惯例路径
            this.configFile = Paths.get("config", "lichenhistorycli", "lichen-history-cli.json");
        }

        JsonObject root = HistoryCliConfigIO.loadOrCreate(configFile);

        Path history = Paths.get("local", "historycli", "command_history.log");
        try {
            Files.createDirectories(history.getParent());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Cannot create history directory " + history.getParent(), e);
        }
        store = new HistoryStore(history);
        applyConfig(root);
        try {
            store.read();
        } catch (Exception e) {
            // 历史文件损坏时用空历史继续，而不是让整个插件加载失败
            LOG.warning("Failed to read history file, starting with an empty history: " + e);
        }

        startFlush();
        LOG.info("Lichen History CLI (Sponge) enabled, language=" + language(root));
    }

    /**
     * 注册命令：Sponge 没有声明式命令描述符，只能在生命周期事件里注册。
     *
     * <p><b>必须声明为 {@code RegisterCommandEvent<Command.Raw>}</b>——Sponge 会为每一种命令类型
     * 各发一次该事件（{@code Parameterized} / {@code Raw} / …），泛型参数就是该次事件的命令类型。
     * 之前声明成父类型 {@code RegisterCommandEvent<Command>} 会<b>匹配到所有次</b>：
     * 在 Raw 那次注册成功，在 Parameterized 那次则被平台内部强转，
     * 抛出 {@code ClassCastException: RawHistoryCommand cannot be cast to Command$Parameterized}
     * （Sponge 26.3 实测踩到）。收窄到 Raw 后只收到自己该收的那次。</p>
     */
    @Listener
    public void onRegisterCommands(RegisterCommandEvent<Command.Raw> event) {
        if (container == null) {
            LOG.warning("Plugin container not ready, commands are not registered");
            return;
        }
        try {
            event.register(container, new RawHistoryCommand(true),
                    "historycliserver", "historyclis", "historycliser");
            event.register(container, new RawHistoryCommand(false),
                    "historyserver", "historys", "historyser");
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Failed to register commands", t);
        }
    }

    /**
     * 录制与 {@code !} 展开：玩家与控制台都走这一个事件。
     *
     * <p>{@code command()} 只是<b>命令名</b>（javadoc：{@code /example bob 3 -f} → {@code example}），
     * 完整行要 {@code command() + " " + arguments()} 拼出来。</p>
     */
    @Listener
    public void onExecute(ExecuteCommandEvent.Pre event) {
        if (store == null) {
            return;
        }
        String name = event.command();
        if (name == null || name.isEmpty()) {
            return;
        }
        String args = event.arguments();
        String line = (args == null || args.isEmpty()) ? name : name + " " + args;

        if (line.charAt(0) == '!') {
            String expanded = store.resolve(line);
            if (expanded == null || expanded.startsWith("!")) {
                // 无匹配，或历史里存的是字面量 `!!`（继续展开会无限递归）
                event.setCancelled(true);
                event.commandCause().audience()
                        .sendMessage(text(tr(HistoryCommandHandler.Keys.NO_MATCH, line)));
                return;
            }
            store.add(expanded);
            String command = HistoryCommandHandler.stripLeadingSlash(expanded);
            int space = command.indexOf(' ');
            event.setCommand(space < 0 ? command : command.substring(0, space));
            event.setArguments(space < 0 ? "" : command.substring(space + 1));
            return;
        }
        store.add(line);
    }

    /** 停服：停掉定时落盘并同步写盘（与 Bukkit 版同一行为）。 */
    @Listener
    public void onShutdown(StoppingEngineEvent<Server> event) {
        if (flushTask != null) {
            try {
                flushTask.cancel();
            } catch (Throwable t) {
                LOG.log(Level.FINE, "Cannot cancel flush task", t);
            }
            flushTask = null;
        }
        if (store != null) {
            try {
                store.write();
                LOG.info("Lichen History CLI (Sponge) history saved");
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Failed to save history on shutdown", t);
            }
        }
    }

    // ---------------------------------------------------------------- 内部逻辑

    /** 每分钟把脏行异步写盘：进程被 kill 也不丢本次会话记录（与 paper 版一致）。 */
    private void startFlush() {
        try {
            flushTask = Sponge.asyncScheduler().submit(
                    Task.builder()
                            .plugin(container)
                            .execute(() -> {
                                try {
                                    if (store != null && store.isDirty()) {
                                        store.write();
                                    }
                                } catch (Throwable t) {
                                    LOG.warning("Periodic history flush failed: " + t);
                                }
                            })
                            .interval(1, TimeUnit.MINUTES)
                            .build());
        } catch (Throwable t) {
            // 定时落盘是增益项，失败不能影响插件其余功能（退化为仅停服时写盘）
            LOG.warning("Periodic history flush unavailable on this Sponge build: " + t);
        }
    }

    private boolean reload() {
        try {
            JsonObject root = HistoryCliConfigIO.loadOrCreate(configFile);
            applyConfig(root);
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to reload config", e);
            return false;
        }
    }

    /** 语言 + 记录开关 + 历史上限，reload 与首启共用。 */
    private void applyConfig(JsonObject root) {
        if (store != null) {
            store.setRecordEnabled(HistoryCliConfigIO.getBool(root, "server", "record_history", true));
            store.setMaxSize(HistoryCliConfigIO.getInt(root, "server", "history_size", 500));
        }
        loadMessages(language(root));
    }

    private String language(JsonObject root) {
        try {
            if (root.has("language")) {
                String value = root.get("language").getAsString();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Bad language value: " + e);
        }
        return "en_us";
    }

    private void loadMessages(String language) {
        Map<String, String> map = loadLang(language);
        if (map.isEmpty() && !"en_us".equals(language)) {
            LOG.warning("Language file '" + language + ".json' missing or broken, falling back to en_us");
            map = loadLang("en_us");
        }
        this.messages = map;
    }

    private Map<String, String> loadLang(String language) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(language + ".json")) {
            if (in == null) {
                return map;
            }
            JsonObject obj = com.google.gson.JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            obj.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            LOG.warning("Failed to load " + language + ".json: " + e);
        }
        return map;
    }

    private String tr(String key, Object... args) {
        String result = messages.getOrDefault(key, key);
        if (args != null) {
            for (Object arg : args) {
                result = result.replaceFirst("%s",
                        java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
            }
        }
        return result;
    }

    private static Component text(String value) {
        return Component.text(value);
    }

    /**
     * 权限判定：非玩家（控制台/命令方块/函数）一律放行，玩家按子命令节点判定。
     *
     * <p>Sponge 没有 {@code isOp()} 之类 API——op 在 Sponge 属于权限体系
     * （原版命令本身就是靠权限节点放行的，所以 op 玩家天然拥有节点），
     * 这里无法也不必复刻 Bukkit 的 {@code isOp()} 短路。</p>
     */
    private boolean permitted(CommandCause cause, String[] args, boolean allowFull) {
        if (!(cause.first(ServerPlayer.class).isPresent())) {
            return true;
        }
        HistoryParser.Result result = HistoryParser.parse(args);
        HistoryParser.Action action = result == null ? null : result.action;
        if (!allowFull && (action == HistoryParser.Action.LIST_EXPLICIT
                || action == HistoryParser.Action.DELETE
                || action == HistoryParser.Action.RELOAD
                || action == HistoryParser.Action.EXECUTE)) {
            return false;
        }
        return PermissionNode.isGranted(cause::hasPermission, action);
    }

    /** 补全：Sponge 的 javadoc 明确「补全被选中时会**替换最后一个词**」，故只需返回最后一个词。 */
    private List<String> suggest(CommandCause cause, List<String> args, boolean allowFull) {
        String[] argv = args.toArray(new String[0]);
        if (!permitted(cause, argv, allowFull)) {
            return List.of();
        }
        List<String> base = allowFull
                ? List.of("list", "-c", "-w", "-a", "-r", "-d", "reload", "help", "?", "!!")
                : List.of("-c", "-w", "-a", "-r", "help", "?");
        if (argv.length == 0) {
            return base;
        }
        String last = argv[argv.length - 1];
        List<String> out = new ArrayList<>();
        for (String option : base) {
            if (option.startsWith(last)) {
                out.add(option);
            }
        }
        return out;
    }

    /** 从参数读取器取原始文本，并剔除可能混入的命令名（不同实现切片方式不一）。 */
    private static String rawArguments(ArgumentReader.Mutable arguments) {
        String raw = arguments.cursor() == 0 ? arguments.input() : arguments.remaining();
        if (raw == null) {
            return "";
        }
        List<String> parts = new ArrayList<>(HistoryParser.split(raw));
        if (!parts.isEmpty() && NAMES.contains(parts.get(0))) {
            parts.remove(0);
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 命令实现

    /** 一个 {@code Command.Raw}：拿到整段参数串，交给与其它平台同一个处理器。 */
    private final class RawHistoryCommand implements Command.Raw {

        private final boolean allowFull;

        private RawHistoryCommand(boolean allowFull) {
            this.allowFull = allowFull;
        }

        @Override
        public CommandResult process(CommandCause cause, ArgumentReader.Mutable arguments) throws CommandException {
            String[] args = HistoryParser.split(rawArguments(arguments)).toArray(new String[0]);
            if (!permitted(cause, args, allowFull)) {
                cause.audience().sendMessage(text(tr(HistoryCommandHandler.Keys.NO_PERMISSION)));
                return CommandResult.success();
            }
            // 结果消息由处理器下发；Sponge 没有 /execute 式退出码诉求，
            // 恒返 success 与 Bukkit 路径（CommandExecutor 恒 true）保持一致。
            new SpongeHandler(cause, allowFull).handle(args);
            return CommandResult.success();
        }

        @Override
        public List<CommandCompletion> complete(CommandCause cause, ArgumentReader.Mutable arguments) {
            String raw = rawArguments(arguments);
            int lastSpace = raw.lastIndexOf(' ');
            String typed = lastSpace < 0 ? raw : raw.substring(lastSpace + 1);
            String already = lastSpace < 0 ? "" : raw.substring(0, lastSpace + 1);

            List<String> args = new ArrayList<>(HistoryParser.split(already));
            args.add(typed); // 正在输入的 token，可能为空串

            List<CommandCompletion> out = new ArrayList<>();
            for (String option : suggest(cause, args, allowFull)) {
                out.add(CommandCompletion.of(option));
            }
            return out;
        }

        @Override
        public boolean canExecute(CommandCause cause) {
            return permitted(cause, new String[0], allowFull);
        }

        @Override
        public Optional<Component> shortDescription(CommandCause cause) {
            return Optional.of(text(tr(HistoryCommandHandler.Keys.HELP_TITLE)));
        }

        @Override
        public Optional<Component> extendedDescription(CommandCause cause) {
            return Optional.of(text(tr(allowFull
                    ? HistoryCommandHandler.Keys.HELP_OPTIONS
                    : HistoryCommandHandler.Keys.HELP_OPTIONS_PLAIN)));
        }

        @Override
        public Component usage(CommandCause cause) {
            return text(tr(HistoryCommandHandler.Keys.HELP_USAGE));
        }
    }

    /** 平台适配：把翻译键/行文本经 Adventure 下发，展开命令经 CommandManager 分发。 */
    private final class SpongeHandler extends HistoryCommandHandler {

        private final CommandCause cause;

        private SpongeHandler(CommandCause cause, boolean allowFull) {
            super(store, allowFull);
            this.cause = cause;
        }

        @Override
        protected void sendMessage(String key, Object... args) {
            cause.audience().sendMessage(text(tr(key, args)));
        }

        @Override
        protected void sendError(String key, Object... args) {
            cause.audience().sendMessage(text(tr(key, args)));
        }

        @Override
        protected void sendRow(String row) {
            cause.audience().sendMessage(text(row));
        }

        @Override
        protected void executeCommand(String command) {
            try {
                Sponge.server().commandManager().process(cause.subject(), cause.audience(), command);
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Failed to execute expanded command: " + command, t);
            }
        }

        @Override
        protected boolean reloadConfig() {
            return reload();
        }
    }
}
