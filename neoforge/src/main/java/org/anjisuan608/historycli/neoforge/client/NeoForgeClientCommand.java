package org.anjisuan608.historycli.neoforge.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.HistorySuggestions;
import org.anjisuan608.historycli.neoforge.HistoryCliNeoForge;

/**
 * NeoForge 客户端命令：/historycliclient（完整）与 /historyclient（普通），以及短名 historycli/history。
 */
public final class NeoForgeClientCommand {

    private NeoForgeClientCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        ClientHandler full = new ClientHandler(HistoryCliNeoForge.clientStore, true);
        ClientHandler plain = new ClientHandler(HistoryCliNeoForge.clientStore, false);

        dispatcher.register(build(Commands.literal("historycliclient"), full));
        registerBestEffort(dispatcher, build(Commands.literal("historycli"), full));
        registerBestEffort(dispatcher, build(Commands.literal("historyclic"), full));
        dispatcher.register(build(Commands.literal("historyclient"), plain));
        registerBestEffort(dispatcher, build(Commands.literal("history"), plain));
        registerBestEffort(dispatcher, build(Commands.literal("historyc"), plain));
    }

    private static void registerBestEffort(CommandDispatcher<CommandSourceStack> dispatcher,
                                           LiteralArgumentBuilder<CommandSourceStack> command) {
        if (dispatcher.getRoot().getChild(command.getLiteral()) == null) {
            dispatcher.register(command);
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(
            LiteralArgumentBuilder<CommandSourceStack> builder,
            ClientHandler handler) {
        return builder
                .executes(ctx -> {
                    handler.setSource(ctx.getSource());
                    handler.handle(new String[0]);
                    return 1;
                })
                .then(Commands.argument("rest", StringArgumentType.greedyString())
                        .suggests(HistorySuggestions.suggest(handler.allowFull()))
                        .executes(ctx -> {
                            handler.setSource(ctx.getSource());
                            handler.handle(HistoryParser.split(StringArgumentType.getString(ctx, "rest")).toArray(new String[0]));
                            return 1;
                        }));
    }

    private static final class ClientHandler extends HistoryCommandHandler {

        private CommandSourceStack source;

        ClientHandler(HistoryStore store, boolean allowFull) {
            super(store, allowFull);
        }

        void setSource(CommandSourceStack source) {
            this.source = source;
        }

        @Override
        protected HistoryStore store() {
            return HistoryCliNeoForge.clientStore;
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

        private static Component tr(String key, Object... args) {
            if (args == null || args.length == 0) {
                return Component.translatable(key);
            }
            java.util.List<Component> list = new java.util.ArrayList<>();
            for (Object a : args) {
                list.add(Component.literal(String.valueOf(a)));
            }
            return Component.translatable(key, key, list);
        }

        @Override
        protected void executeCommand(String command) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand(command);
            }
        }
    }
}
