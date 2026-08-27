package org.anjisuan608.historycli.bukkit;

import org.anjisuan608.historycli.HistoryCommandHandler;
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

        getCommand("historycliserver").setExecutor(bukkitCommand(true));
        getCommand("historyserver").setExecutor(bukkitCommand(false));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Lichen History CLI (Bukkit) enabled");
    }

    private org.bukkit.command.CommandExecutor bukkitCommand(boolean allowFull) {
        return (sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage("No permission");
                return true;
            }
            new BukkitHandler(store, sender, allowFull).handle(args);
            return true;
        };
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

    private static final class BukkitHandler extends HistoryCommandHandler {

        private final CommandSender sender;

        BukkitHandler(HistoryStore store, CommandSender sender, boolean allowFull) {
            super(store, allowFull);
            this.sender = sender;
        }

        @Override
        protected void sendMessage(String message) {
            sender.sendMessage(message);
        }

        @Override
        protected void sendError(String message) {
            sender.sendMessage(message);
        }

        @Override
        protected void executeCommand(String command) {
            Bukkit.dispatchCommand(sender, command);
        }
    }
}
