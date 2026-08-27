package org.anjisuan608.historycli.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryStore;

import java.io.File;

public final class BungeeHistoryPlugin extends Plugin implements Listener {

    private HistoryStore store;

    @Override
    public void onEnable() {
        File file = new File(getDataFolder(), "command_history.log");
        file.getParentFile().mkdirs();
        store = new HistoryStore(file.toPath());
        store.setMaxSize(0);
        store.read();

        getProxy().getPluginManager().registerCommand(this, bungeeCommand("historycliproxy", true, "historyclipro", "historyclip"));
        getProxy().getPluginManager().registerCommand(this, bungeeCommand("historyproxy", false, "historypro", "historyp"));
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("Lichen History CLI (BungeeCord) loaded");
    }

    private Command bungeeCommand(String name, boolean allowFull, String... aliases) {
        return new Command(name, "", aliases) {
            @Override
            public void execute(CommandSender sender, String[] args) {
                new BungeeHandler(BungeeHistoryPlugin.this, store, sender, allowFull).handle(args);
            }
        };
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

    private static final class BungeeHandler extends HistoryCommandHandler {

        private final BungeeHistoryPlugin plugin;
        private final CommandSender sender;

        BungeeHandler(BungeeHistoryPlugin plugin, HistoryStore store, CommandSender sender, boolean allowFull) {
            super(store, allowFull);
            this.plugin = plugin;
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
            plugin.getProxy().getPluginManager().dispatchCommand(sender, command);
        }
    }

    @Override
    public void onDisable() {
    }
}
