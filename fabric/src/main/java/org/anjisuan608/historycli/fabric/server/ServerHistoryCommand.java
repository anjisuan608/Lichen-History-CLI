package org.anjisuan608.historycli.fabric.server;

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
import org.anjisuan608.historycli.fabric.HistoryCliFabric;

/**
 * 服务端命令：/historycliserver（完整）与 /historyserver（普通）。仅专用服务器（GAMEMASTER 权限）。
 * 逻辑委托给 {@link HistoryCommandHandler}（common）。
 */
public final class ServerHistoryCommand {

    private ServerHistoryCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        boolean integrated = selection == Commands.CommandSelection.INTEGRATED;
        if (integrated && !HistoryCliFabric.enableIntegratedHistory) {
            return;
        }
        if (integrated) {
            ServerHandler full = new ServerHandler(null, true);
            ServerHandler plain = new ServerHandler(null, false);
            dispatcher.register(build(Commands.literal("historycliserver"), full));
            registerBestEffort(dispatcher, build(Commands.literal("historyclis"), full));
            registerBestEffort(dispatcher, build(Commands.literal("historycliser"), full));
            dispatcher.register(build(Commands.literal("historyserver"), plain));
            registerBestEffort(dispatcher, build(Commands.literal("historys"), plain));
            registerBestEffort(dispatcher, build(Commands.literal("historyser"), plain));
            return;
        }
        if (HistoryCliFabric.serverStore == null) {
            return;
        }
        ServerHandler full = new ServerHandler(HistoryCliFabric.serverStore, true);
        ServerHandler plain = new ServerHandler(HistoryCliFabric.serverStore, false);
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
            ServerHandler handler) {
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

    private static final class ServerHandler extends HistoryCommandHandler {

        private CommandSourceStack source;

        ServerHandler(HistoryStore store, boolean allowFull) {
            super(store, allowFull);
        }

        void setSource(CommandSourceStack source) {
            this.source = source;
        }

        @Override
        protected HistoryStore store() {
            return HistoryCliFabric.serverStore;
        }

        @Override
        protected boolean reloadConfig() {
            return HistoryCliFabric.reloadConfig();
        }

        @Override
        protected void sendMessage(String key, Object... args) {
            if (source != null) {
                source.sendSuccess(() -> Component.translatable(key, args), false);
            }
        }

        @Override
        protected void sendError(String key, Object... args) {
            if (source != null) {
                source.sendFailure(Component.translatable(key, args));
            }
        }

        @Override
        protected void sendRow(String text) {
            if (source != null) {
                source.sendSuccess(() -> Component.literal(text), false);
            }
        }

        @Override
        protected void executeCommand(String command) {
            if (source != null && source.getServer() != null) {
                source.getServer().getCommands().performPrefixedCommand(source, command);
            }
        }
    }
}
