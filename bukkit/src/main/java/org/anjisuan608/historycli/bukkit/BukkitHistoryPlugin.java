package org.anjisuan608.historycli.bukkit;

import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class BukkitHistoryPlugin extends JavaPlugin implements Listener {

    private HistoryStore store;

    @Override
    public void onEnable() {
        File file = new File("local/historycli/command_history.log");
        file.getParentFile().mkdirs();
        store = new HistoryStore(file.toPath());
        store.setMaxSize(0);
        store.read();

        getCommand("historycliserver").setExecutor(this::handle);
        getCommand("historyserver").setExecutor(this::handle);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Lichen History CLI (Bukkit) enabled");
    }

    private boolean handle(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("No permission");
            return true;
        }
        HistoryParser.Result result = HistoryParser.parse(args);
        if (result == null) {
            return true;
        }
        switch (result.action) {
            case EMPTY:
            case LIST:
                list(sender, result.number);
                break;
            case EXECUTE:
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
                if (store.delete(result.number)) {
                    sender.sendMessage("Deleted history entry " + result.number);
                } else {
                    sender.sendMessage("Invalid history index " + result.number);
                }
                break;
            case RELOAD:
                store.read();
                sender.sendMessage("Config reloaded");
                break;
            default:
                sender.sendMessage("Unknown command");
                break;
        }
        return true;
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
        Bukkit.dispatchCommand(sender, expanded);
    }

    private void list(CommandSender sender, int count) {
        java.util.List<String> snapshot = store.snapshot();
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

    @EventHandler(priority = EventPriority.LOW)
    public void onServerCommand(ServerCommandEvent event) {
        process(event.getSender(), event.getCommand(), event::setCommand);
    }

    private void process(CommandSender sender, String command, java.util.function.Consumer<String> setter) {
        if (command == null || command.isEmpty()) {
            return;
        }
        if (command.startsWith("!")) {
            String expanded = store.resolve(command);
            if (expanded != null) {
                store.add(expanded);
                setter.accept(expanded.startsWith("/") ? expanded.substring(1) : expanded);
            }
        } else {
            store.add(command);
        }
    }
}
