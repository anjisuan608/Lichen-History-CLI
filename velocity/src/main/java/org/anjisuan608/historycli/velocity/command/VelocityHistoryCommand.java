package org.anjisuan608.historycli.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.PermissionNode;
import org.anjisuan608.historycli.velocity.VelocityHistoryPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Velocity 命令基类：两个命令共用注册/补全/鉴权/分发逻辑。
 *
 * <p><b>每次调用新建 handler</b>：Velocity 把命令执行放到插件的线程池里跑，
 * 若共享一个 handler 实例再往字段里塞 source，两个玩家并发执行时会互相串台——
 * 轻则回错人，重则以别人的身份（例如控制台）执行 {@code !!} 展开的命令。</p>
 */
abstract class VelocityHistoryCommand implements SimpleCommand {

    private static final List<String> FULL_CANDIDATES = List.of(
            "list", "-c", "-w", "-a", "-r", "-d", "reload", "help", "?", "!!");
    private static final List<String> PLAIN_CANDIDATES = List.of("-c", "-w", "-a", "-r", "help", "?");

    private final HistoryStore store;
    private final boolean allowFull;
    private final Supplier<Map<String, String>> messages;
    private final ProxyServer server;
    private final BooleanSupplier configReloader;

    VelocityHistoryCommand(HistoryStore store, boolean allowFull, Supplier<Map<String, String>> messages,
                           ProxyServer server, BooleanSupplier configReloader) {
        this.store = store;
        this.allowFull = allowFull;
        this.messages = messages;
        this.server = server;
        this.configReloader = configReloader;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!permitted(source, invocation.arguments())) {
            source.sendMessage(Component.text(tr(HistoryCommandHandler.Keys.NO_PERMISSION))
                    .color(NamedTextColor.RED));
            return;
        }
        new VelocityHandler(store, allowFull, messages, server, configReloader, source)
                .handle(invocation.arguments());
    }

    /** 按输入前缀过滤补全（原实现无条件返回全量列表）。 */
    @Override
    public List<String> suggest(Invocation invocation) {
        if (!permitted(invocation.source(), invocation.arguments())) {
            return List.of();
        }
        String[] args = invocation.arguments();
        String prefix = args.length == 0 ? "" : args[args.length - 1];
        List<String> candidates = allowFull ? FULL_CANDIDATES : PLAIN_CANDIDATES;
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(prefix)) {
                out.add(candidate);
            }
        }
        return out;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return permitted(invocation.source(), invocation.arguments());
    }

    private boolean permitted(CommandSource source, String[] args) {
        HistoryParser.Result result = HistoryParser.parse(args);
        HistoryParser.Action action = result == null ? HistoryParser.Action.LIST : result.action;
        return PermissionNode.isGranted(source::hasPermission, action);
    }

    private String tr(String key, Object... args) {
        return VelocityHistoryPlugin.tr(messages.get(), key, args);
    }

    private static final class VelocityHandler extends HistoryCommandHandler {

        private final Supplier<Map<String, String>> messages;
        private final ProxyServer server;
        private final BooleanSupplier configReloader;
        private final CommandSource source;

        VelocityHandler(HistoryStore store, boolean allowFull, Supplier<Map<String, String>> messages,
                        ProxyServer server, BooleanSupplier configReloader, CommandSource source) {
            super(store, allowFull);
            this.messages = messages;
            this.server = server;
            this.configReloader = configReloader;
            this.source = source;
        }

        @Override
        protected void sendMessage(String key, Object... args) {
            source.sendMessage(Component.text(VelocityHistoryPlugin.tr(messages.get(), key, args)));
        }

        @Override
        protected void sendError(String key, Object... args) {
            source.sendMessage(Component.text(VelocityHistoryPlugin.tr(messages.get(), key, args))
                    .color(NamedTextColor.RED));
        }

        @Override
        protected void sendRow(String text) {
            source.sendMessage(Component.text(text));
        }

        @Override
        protected void executeCommand(String command) {
            if (server == null) {
                // 普通命令不支持 ! 展开（common 层已拒绝），这里是防御：
                // 万一走到，也要给明确反馈而不是静默无操作
                sendError(HistoryCommandHandler.Keys.NOT_EXECUTABLE, command);
                return;
            }
            // 用 executeAsync 而不是 executeImmediatelyAsync：
            // 后者绕过其它插件的 CommandExecuteEvent，会让黑名单/审计类插件对 ! 展开的命令失效。
            // 展开结果由本类先 store.add 一次，随后录制事件会再记一次 → 连续重复被 ignoredups 吞掉。
            server.getCommandManager().executeAsync(source, command)
                    .thenAccept(executed -> {
                        if (executed == null || !executed) {
                            sendError(HistoryCommandHandler.Keys.NOT_EXECUTABLE, command);
                        }
                    })
                    .exceptionally(ex -> {
                        // 插件命令抛异常时 future 以异常完成，原先用户收不到任何反馈
                        sendError(HistoryCommandHandler.Keys.NOT_EXECUTABLE, command);
                        return null;
                    });
        }

        @Override
        protected boolean reloadConfig() {
            return configReloader != null && configReloader.getAsBoolean();
        }
    }
}
