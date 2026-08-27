package org.anjisuan608.historycli.fabric.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;

import java.util.List;

/**
 * 客户端命令 /historycli（别名 /history）。
 * <p>主命令支持全部子命令；别名 /history 仅 bash 标准子集（不含 reload 与 ! 系列）。</p>
 */
public final class ClientHistoryCommand {

    private ClientHistoryCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                ClientCommands.literal("historycli")
                        .executes(ctx -> run(ctx.getSource(), ""))
                        .then(ClientCommands.argument("rest", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "rest"))))
        );
        dispatcher.register(
                ClientCommands.literal("history")
                        .executes(ctx -> run(ctx.getSource(), ""))
                        .then(ClientCommands.argument("rest", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "rest"))))
        );
    }

    private static int run(FabricClientCommandSource source, String rest) {
        HistoryStore store = HistoryCliFabricClient.store;
        if (store == null) {
            source.sendError(Component.literal("History store not initialized"));
            return 0;
        }

        List<String> args = HistoryParser.split(rest);
        HistoryParser.Result result = HistoryParser.parse(args.toArray(new String[0]));
        if (result == null) {
            return 0;
        }

        switch (result.action) {
            case EMPTY:
            case LIST:
                list(source, store, result.number);
                return 1;
            case EXECUTE:
                execute(source, store, result.command);
                return 1;
            case CLEAR:
                store.reset();
                source.sendFeedback(Component.literal("History cleared"));
                return 1;
            case WRITE:
                store.write();
                source.sendFeedback(Component.literal("History written to file"));
                return 1;
            case APPEND:
                store.append();
                source.sendFeedback(Component.literal("History appended to file"));
                return 1;
            case READ:
                store.read();
                source.sendFeedback(Component.literal("History loaded from file"));
                return 1;
            case DELETE:
                if (store.delete(result.number)) {
                    source.sendFeedback(Component.literal("Deleted history entry " + result.number));
                } else {
                    source.sendError(Component.literal("Invalid history index " + result.number));
                }
                return 1;
            case RELOAD:
                store.read();
                source.sendFeedback(Component.literal("Config reloaded"));
                return 1;
            default:
                source.sendError(Component.literal("Unknown command"));
                return 0;
        }
    }

    private static void execute(FabricClientCommandSource source, HistoryStore store, String bang) {
        if (bang == null) {
            return;
        }
        String expanded = store.resolve(bang);
        if (expanded == null) {
            source.sendError(Component.literal("No matching history entry for '" + bang + "'"));
            return;
        }
        store.add(expanded);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(expanded);
        }
    }

    private static void list(FabricClientCommandSource source, HistoryStore store, int count) {
        List<String> snapshot = store.snapshot();
        int start = 0;
        int total = snapshot.size();
        if (count > 0 && count < total) {
            start = total - count;
        }
        if (total == 0) {
            source.sendFeedback(Component.literal("No history"));
            return;
        }
        for (int i = start; i < total; i++) {
            source.sendFeedback(Component.literal((i + 1) + "  " + snapshot.get(i)));
        }
    }
}
