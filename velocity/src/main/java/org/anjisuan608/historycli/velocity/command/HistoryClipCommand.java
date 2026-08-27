package org.anjisuan608.historycli.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.PermissionNode;

/**
 * 完整命令 /historycliproxy（别名 historyclipro/historyclip）：全部功能（含 reload 与 ! 系列）。
 * 逻辑委托给 {@link HistoryCommandHandler}（common）。
 */
public final class HistoryClipCommand implements SimpleCommand {

    private final ProxyServer server;
    private final VelocityHandler handler;

    public HistoryClipCommand(HistoryStore store, ProxyServer server) {
        this.server = server;
        this.handler = new VelocityHandler(store, true);
    }

    @Override
    public void execute(Invocation invocation) {
        handler.setSource(invocation.source());
        handler.handle(invocation.arguments());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        HistoryParser.Result r = HistoryParser.parse(invocation.arguments());
        HistoryParser.Action action = r == null ? HistoryParser.Action.LIST : r.action;
        return invocation.source().hasPermission(PermissionNode.forAction(action))
                || invocation.source().hasPermission(PermissionNode.ALL);
    }

    private final class VelocityHandler extends HistoryCommandHandler {

        private CommandSource source;

        VelocityHandler(HistoryStore store, boolean allowFull) {
            super(store, allowFull);
        }

        void setSource(CommandSource source) {
            this.source = source;
        }

        @Override
        protected void sendMessage(String message) {
            if (source != null) {
                source.sendMessage(Component.text(message));
            }
        }

        @Override
        protected void sendError(String message) {
            if (source != null) {
                source.sendMessage(Component.text(message));
            }
        }

        @Override
        protected void executeCommand(String command) {
            if (source != null) {
                server.getCommandManager().executeImmediatelyAsync(source, command);
            }
        }
    }
}
