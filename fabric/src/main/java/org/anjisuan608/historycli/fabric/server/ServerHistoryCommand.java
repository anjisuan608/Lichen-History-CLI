package org.anjisuan608.historycli.fabric.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.HistoryCliFabric;

import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

/**
 * 服务端命令：/historycliserver（完整命令，含 reload 与 ! 系列）与 /historyserver（普通命令，bash 标准子集）。
 * 仅专用服务器（需命令等级 GAMEMASTER，等价原 op level 2）。
 */
public final class ServerHistoryCommand {

    private ServerHistoryCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        if (selection == Commands.CommandSelection.INTEGRATED || HistoryCliFabric.serverStore == null) {
            return;
        }
        dispatcher.register(
                Commands.literal("historycliserver")
                        .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> run(ctx.getSource(), ""))
                        .then(Commands.argument("rest", greedyString())
                                .executes(ctx -> run(ctx.getSource(), getString(ctx, "rest"))))
        );
        dispatcher.register(
                Commands.literal("historyserver")
                        .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> run(ctx.getSource(), ""))
                        .then(Commands.argument("rest", greedyString())
                                .executes(ctx -> run(ctx.getSource(), getString(ctx, "rest"))))
        );
    }

    private static int run(CommandSourceStack source, String rest) {
        HistoryStore store = HistoryCliFabric.serverStore;
        if (store == null) {
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
                source.sendSuccess(() -> Component.literal("History cleared"), false);
                return 1;
            case WRITE:
                store.write();
                source.sendSuccess(() -> Component.literal("History written to file"), false);
                return 1;
            case APPEND:
                store.append();
                source.sendSuccess(() -> Component.literal("History appended to file"), false);
                return 1;
            case READ:
                store.read();
                source.sendSuccess(() -> Component.literal("History loaded from file"), false);
                return 1;
            case DELETE:
                if (store.delete(result.number)) {
                    source.sendSuccess(() -> Component.literal("Deleted history entry " + result.number), false);
                } else {
                    source.sendFailure(Component.literal("Invalid history index " + result.number));
                }
                return 1;
            case RELOAD:
                store.read();
                source.sendSuccess(() -> Component.literal("Config reloaded"), false);
                return 1;
            default:
                source.sendFailure(Component.literal("Unknown command"));
                return 0;
        }
    }

    private static void execute(CommandSourceStack source, HistoryStore store, String bang) {
        if (bang == null) {
            return;
        }
        String expanded = store.resolve(bang);
        if (expanded == null) {
            source.sendFailure(Component.literal("No matching history entry for '" + bang + "'"));
            return;
        }
        store.add(expanded);
        source.getServer().getCommands().performPrefixedCommand(source, expanded);
    }

    private static void list(CommandSourceStack source, HistoryStore store, int count) {
        List<String> snapshot = store.snapshot();
        int start = 0;
        int total = snapshot.size();
        if (count > 0 && count < total) {
            start = total - count;
        }
        if (total == 0) {
            source.sendSuccess(() -> Component.literal("No history"), false);
            return;
        }
        for (int i = start; i < total; i++) {
            final int index = i + 1;
            final String line = snapshot.get(i);
            source.sendSuccess(() -> Component.literal(index + "  " + line), false);
        }
    }
}
