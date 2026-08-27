package org.anjisuan608.historycli.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.PermissionNode;

import java.io.File;
import java.util.List;

public final class BungeeHistoryPlugin extends Plugin implements Listener {

    private HistoryStore store;

    @Override
    public void onEnable() {
        File file = new File(getDataFolder(), "command_history.log");
        file.getParentFile().mkdirs();
        store = new HistoryStore(file.toPath());
        store.setMaxSize(0);
        store.read();

        getProxy().getPluginManager().registerCommand(this, new Command("historycliproxy", "", "", "historyclipro", "historyclip") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                handle(sender, args, true);
            }
        });
        getProxy().getPluginManager().registerCommand(this, new Command("historyproxy", "", "", "historypro", "historyp") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                handle(sender, args, false);
            }
        });
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("Lichen History CLI (BungeeCord) loaded");
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        String msg = event.getMessage();
        if (msg == null || msg.isEmpty()) {
            return;
        }
        if (msg.startsWith("/")) {
            String command = msg.substring(1);
            if (!command.isEmpty() && !command.startsWith("!")) {
                store.add(command);
            }
        }
    }

    public void handle(CommandSender sender, String[] args, boolean allowFull) {
        HistoryParser.Result result = HistoryParser.parse(args);
        if (result == null) {
            return;
        }
        String node = PermissionNode.forAction(result.action);
        if (!sender.hasPermission(node) && !sender.hasPermission(PermissionNode.ALL) && !sender.hasPermission(PermissionNode.USE)) {
            sender.sendMessage("No permission");
            return;
        }
        switch (result.action) {
            case EMPTY:
            case LIST:
                list(sender, result.number);
                break;
            case EXECUTE:
                if (!allowFull) {
                    sender.sendMessage("Unsupported for historyproxy");
                    break;
                }
                execute(sender, result.command);
                break;
            case CLEAR:
                store.reset();
                sender.sendMessage("History cleared");
                break;
            case WRITE:
                store.write();
                sender.sendMessage("History written to file");
                break;
            case APPEND:
                store.append();
                sender.sendMessage("History appended to file");
                break;
            case READ:
                store.read();
                sender.sendMessage("History loaded from file");
                break;
            case DELETE:
                if (allowFull && store.delete(result.number)) {
                    sender.sendMessage("Deleted history entry " + result.number);
                } else {
                    sender.sendMessage("Invalid history index " + result.number);
                }
                break;
            case RELOAD:
                if (allowFull) {
                    store.read();
                    sender.sendMessage("Config reloaded");
                } else {
                    sender.sendMessage("Unsupported for historyproxy");
                }
                break;
            default:
                sender.sendMessage("Unknown command");
                break;
        }
    }

    private void execute(CommandSender sender, String bang) {
        if (bang == null) {
            return;
        }
        String expanded = store.resolve(bang);
        if (expanded == null) {
            sender.sendMessage("No matching history entry for '" + bang + "'");
            return;
        }
        store.add(expanded);
        getProxy().getPluginManager().dispatchCommand(sender, expanded);
    }

    private void list(CommandSender sender, int count) {
        List<String> snapshot = store.snapshot();
        int start = 0;
        int total = snapshot.size();
        if (count > 0 && count < total) {
            start = total - count;
        }
        if (total == 0) {
            sender.sendMessage("No history");
            return;
        }
        for (int i = start; i < total; i++) {
            sender.sendMessage((i + 1) + "  " + snapshot.get(i));
        }
    }
}
