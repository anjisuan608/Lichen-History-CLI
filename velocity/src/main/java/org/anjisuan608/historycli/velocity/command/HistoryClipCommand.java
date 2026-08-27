package org.anjisuan608.historycli.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.PermissionNode;

import java.util.List;

/**
 * 完整命令 /historycliproxy（别名 historyclipro/historyclip）：全部功能（含 reload 与 ! 系列）。
 */
public final class HistoryClipCommand implements SimpleCommand {

    private final HistoryStore store;
    private final ProxyServer server;

    public HistoryClipCommand(HistoryStore store, ProxyServer server) {
        this.store = store;
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        HistoryParser.Result result = HistoryParser.parse(invocation.arguments());
        if (result == null) {
            return;
        }
        switch (result.action) {
            case EMPTY:
            case LIST:
                list(source, result.number);
                break;
            case EXECUTE:
                execute(source, result.command);
                break;
            case CLEAR:
                store.reset();
                source.sendMessage(Component.text("History cleared"));
                break;
            case WRITE:
                store.write();
                source.sendMessage(Component.text("History written to file"));
                break;
            case APPEND:
                store.append();
                source.sendMessage(Component.text("History appended to file"));
                break;
            case READ:
                store.read();
                source.sendMessage(Component.text("History loaded from file"));
                break;
            case DELETE:
                if (store.delete(result.number)) {
                    source.sendMessage(Component.text("Deleted history entry " + result.number));
                } else {
                    source.sendMessage(Component.text("Invalid history index " + result.number));
                }
                break;
            case RELOAD:
                store.read();
                source.sendMessage(Component.text("Config reloaded"));
                break;
            default:
                source.sendMessage(Component.text("Unknown command"));
                break;
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        HistoryParser.Result r = HistoryParser.parse(invocation.arguments());
        HistoryParser.Action action = r == null ? HistoryParser.Action.LIST : r.action;
        return invocation.source().hasPermission(PermissionNode.forAction(action))
                || invocation.source().hasPermission(PermissionNode.ALL);
    }

    private void execute(CommandSource source, String bang) {
        if (bang == null) {
            return;
        }
        String expanded = store.resolve(bang);
        if (expanded == null) {
            source.sendMessage(Component.text("No matching history entry for '" + bang + "'"));
            return;
        }
        store.add(expanded);
        server.getCommandManager().executeImmediatelyAsync(source, expanded);
    }

    private void list(CommandSource source, int count) {
        List<String> snapshot = store.snapshot();
        int start = 0;
        int total = snapshot.size();
        if (count > 0 && count < total) {
            start = total - count;
        }
        if (total == 0) {
            source.sendMessage(Component.text("No history"));
            return;
        }
        for (int i = start; i < total; i++) {
            source.sendMessage(Component.text((i + 1) + "  " + snapshot.get(i)));
        }
    }
}
