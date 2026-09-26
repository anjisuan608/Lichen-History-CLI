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
                .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(ctx -> {
                    handler.setSource(ctx.getSource());
                    return handler.handle(new String[0]) ? 1 : 0;
                })
                .then(Commands.argument("rest", StringArgumentType.greedyString())
                        .suggests(org.anjisuan608.historycli.HistorySuggestions.suggest(handler.allowFull()))
                        .executes(ctx -> {
                            handler.setSource(ctx.getSource());
                            return handler.handle(HistoryParser.split(StringArgumentType.getString(ctx, "rest")).toArray(new String[0]))
                                    ? 1 : 0;
                        }));
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
