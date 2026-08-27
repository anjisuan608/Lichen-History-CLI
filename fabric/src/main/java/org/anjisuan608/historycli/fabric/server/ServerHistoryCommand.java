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
        if (selection == Commands.CommandSelection.INTEGRATED || HistoryCliFabric.serverStore == null) {
            return;
        }
        ServerHandler full = new ServerHandler(HistoryCliFabric.serverStore, true);
        ServerHandler plain = new ServerHandler(HistoryCliFabric.serverStore, false);
        dispatcher.register(build(Commands.literal("historycliserver"), full));
        dispatcher.register(build(Commands.literal("historyserver"), plain));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(
            LiteralArgumentBuilder<CommandSourceStack> builder,
            ServerHandler handler) {
        return builder
                .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(ctx -> {
                    handler.setSource(ctx.getSource());
                    handler.handle(new String[0]);
                    return 1;
                })
                .then(Commands.argument("rest", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            handler.setSource(ctx.getSource());
                            handler.handle(HistoryParser.split(StringArgumentType.getString(ctx, "rest")).toArray(new String[0]));
                            return 1;
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
        protected void sendMessage(String message) {
            if (source != null) {
                source.sendSuccess(() -> Component.literal(message), false);
            }
        }

        @Override
        protected void sendError(String message) {
            if (source != null) {
                source.sendFailure(Component.literal(message));
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
