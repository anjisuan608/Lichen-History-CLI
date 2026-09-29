package org.anjisuan608.historycli.forge.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.forge.HistoryCliForge;

/**
 * Forge 服务端命令：/historycliserver（完整）与 /historyserver（普通）。仅专用服务器。
 * 逻辑委托给 {@link HistoryCommandHandler}（common）。
 */
public final class ForgeServerCommand {

    private ForgeServerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, boolean integrated) {
        ForgeHandler full = integrated
                ? new ForgeHandler(null, true)
                : new ForgeHandler(HistoryCliForge.serverStore, true);
        ForgeHandler plain = integrated
                ? new ForgeHandler(null, false)
                : new ForgeHandler(HistoryCliForge.serverStore, false);
        dispatcher.register(build(Commands.literal("historycliserver"), full));
        registerBestEffort(dispatcher, build(Commands.literal("historyclis"), full));
        registerBestEffort(dispatcher, build(Commands.literal("historycliser"), full));
        dispatcher.register(build(Commands.literal("historyserver"), plain));
        registerBestEffort(dispatcher, build(Commands.literal("historys"), plain));
        registerBestEffort(dispatcher, build(Commands.literal("historyser"), plain));
    }

    private static void registerBestEffort(CommandDispatcher<CommandSourceStack> dispatcher,
                                           LiteralArgumentBuilder<CommandSourceStack> command) {
        if (dispatcher.getRoot().getChild(command.getLiteral()) == null) {
            dispatcher.register(command);
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(
            LiteralArgumentBuilder<CommandSourceStack> builder,
            ForgeHandler handler) {
        return builder
                // 权限判定也要兜住：异常在 parse 阶段逃逸时，原版只会回一句 command.failed
                // 且**不打堆栈**（真机实测），现场无从排查。
                .requires(s -> {
                    try {
                        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                    } catch (Throwable t) {
                        HistoryCliForge.LOGGER.error("[lichenhistorycli] permission check threw", t);
                        return false;
                    }
                })
                .executes(ctx -> {
                    handler.setSource(ctx.getSource());
                    return run(handler, new String[0]);
                })
                .then(Commands.argument("rest", StringArgumentType.greedyString())
                        .suggests(org.anjisuan608.historycli.HistorySuggestions.suggest(handler.allowFull()))
                        .executes(ctx -> {
                            handler.setSource(ctx.getSource());
                            return run(handler, HistoryParser.split(StringArgumentType.getString(ctx, "rest")).toArray(new String[0]));
                        }));
    }

    /**
     * 执行并把未受检异常写进日志：原版把命令异常统一吞成 {@code command.failed} 转译，
     * 控制台只看得到 "An unexpected error occurred trying to execute that command"、
     * <b>没有任何堆栈</b>（真机实测），不自己记一笔就永远查不出根因。
     *
     * @return 1 成功 / 0 失败
     */
    private static int run(ForgeHandler handler, String[] args) {
        try {
            return handler.handle(args) ? 1 : 0;
        } catch (Throwable t) {
            HistoryCliForge.LOGGER.error("[lichenhistorycli] command threw: {}", String.join(" ", args), t);
            return 0;
        }
    }

    private static final class ForgeHandler extends HistoryCommandHandler {

        private CommandSourceStack source;

        ForgeHandler(HistoryStore store, boolean allowFull) {
            super(store, allowFull);
        }

        void setSource(CommandSourceStack source) {
            this.source = source;
        }

        @Override
        protected HistoryStore store() {
            return HistoryCliForge.serverStore;
        }

        @Override
        protected void sendMessage(String key, Object... args) {
            if (source != null) {
                source.sendSuccess(() -> tr(key, args), false);
            }
        }

        @Override
        protected void sendError(String key, Object... args) {
            if (source != null) {
                source.sendFailure(tr(key, args));
            }
        }

        @Override
        protected void sendRow(String text) {
            if (source != null) {
                source.sendSuccess(() -> Component.literal(text), false);
            }
        }

        /**
         * 把实参转成字符串字面量后展开传入（修复 {@code translatable(key, key, list)} 误传 key 的问题）。
         */
        private static Component tr(String key, Object... args) {
            if (args == null || args.length == 0) {
                return Component.translatable(key);
            }
            Object[] converted = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                converted[i] = Component.literal(String.valueOf(args[i]));
            }
            return Component.translatable(key, converted);
        }

        @Override
        protected boolean reloadConfig() {
            return HistoryCliForge.reloadConfig();
        }

        @Override
        protected void executeCommand(String command) {
            if (source != null && source.getServer() != null) {
                source.getServer().getCommands().performPrefixedCommand(source, command);
            }
        }
    }
}
