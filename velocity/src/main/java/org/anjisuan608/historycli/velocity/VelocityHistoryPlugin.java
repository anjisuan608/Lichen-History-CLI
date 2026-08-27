package org.anjisuan608.historycli.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.velocity.command.HistoryProxyCommand;
import org.anjisuan608.historycli.velocity.command.HistoryClipCommand;
import org.slf4j.Logger;

import java.nio.file.Path;

public final class VelocityHistoryPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private HistoryStore store;

    @Inject
    public VelocityHistoryPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        Path file = dataDirectory.resolve("command_history.log");
        this.store = new HistoryStore(file);
        this.store.setMaxSize(0);
        this.store.read();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        CommandManager manager = server.getCommandManager();
        manager.register(manager.metaBuilder("historycliproxy").aliases("historyclipro", "historyclip").build(),
                new HistoryClipCommand(store, server));
        manager.register(manager.metaBuilder("historyproxy").aliases("historypro", "historyp").build(),
                new HistoryProxyCommand(store));
        logger.info("Lichen History CLI (Velocity) loaded");
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        String cmd = event.getCommand();
        if (cmd != null && !cmd.isEmpty()) {
            store.add(cmd);
        }
    }

    public HistoryStore store() {
        return store;
    }

    public CommandManager manager() {
        return server.getCommandManager();
    }
}
