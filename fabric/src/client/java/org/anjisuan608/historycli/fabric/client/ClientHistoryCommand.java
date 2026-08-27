package org.anjisuan608.historycli.fabric.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;

/**
 * 客户端命令体系。
 * <p>完整命令：{@code /historycliclient}（别名 historyclic）+ 短名 /historycli（尽力注册）。
 * 普通命令：{@code /historyclient}（别名 historyc）+ 短名 /history（尽力注册）。
 * 具体逻辑委托给 {@link HistoryCommandHandler}（common），此处仅做命令注册与平台适配。</p>
 */
public final class ClientHistoryCommand {

    private ClientHistoryCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        HistoryStore store = HistoryCliFabricClient.store;
        ClientHandler full = new ClientHandler(store, true);
        ClientHandler plain = new ClientHandler(store, false);

        dispatcher.register(build(ClientCommands.literal("historycliclient"), full));
        registerBestEffort(dispatcher, build(ClientCommands.literal("historycli"), full));
        registerBestEffort(dispatcher, build(ClientCommands.literal("historyclic"), full));
        dispatcher.register(build(ClientCommands.literal("historyclient"), plain));
        registerBestEffort(dispatcher, build(ClientCommands.literal("history"), plain));
        registerBestEffort(dispatcher, build(ClientCommands.literal("historyc"), plain));
    }

    private static void registerBestEffort(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                           LiteralArgumentBuilder<FabricClientCommandSource> command) {
        if (dispatcher.getRoot().getChild(command.getLiteral()) == null) {
            dispatcher.register(command);
        }
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(
            LiteralArgumentBuilder<FabricClientCommandSource> builder,
            ClientHandler handler) {
        return builder
                .executes(ctx -> {
                    handler.setSource(ctx.getSource());
                    handler.handle(new String[0]);
                    return 1;
                })
                .then(ClientCommands.argument("rest", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            handler.setSource(ctx.getSource());
                            handler.handle(HistoryParser.split(StringArgumentType.getString(ctx, "rest")).toArray(new String[0]));
                            return 1;
                        }));
    }

    private static final class ClientHandler extends HistoryCommandHandler {

        private FabricClientCommandSource source;

        ClientHandler(HistoryStore store, boolean allowFull) {
            super(store, allowFull);
        }

        void setSource(FabricClientCommandSource source) {
            this.source = source;
        }

        @Override
        protected void sendMessage(String key, Object... args) {
            if (source != null) {
                source.sendFeedback(tr(key, args));
            }
        }

        @Override
        protected void sendError(String key, Object... args) {
            if (source != null) {
                source.sendError(tr(key, args));
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
        protected void sendRow(String text) {
            if (source != null) {
                source.sendFeedback(Component.literal(text));
            }
        }

        @Override
        protected void executeCommand(String command) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand(command);
            }
        }
    }
}
