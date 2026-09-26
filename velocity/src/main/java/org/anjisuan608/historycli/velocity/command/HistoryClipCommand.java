package org.anjisuan608.historycli.velocity.command;

import com.velocitypowered.api.proxy.ProxyServer;
import org.anjisuan608.historycli.HistoryStore;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 完整命令 /historycliproxy（别名 historyclipro/historyclip）：全部功能（含 reload 与 ! 系列）。
 */
public final class HistoryClipCommand extends VelocityHistoryCommand {

    public HistoryClipCommand(HistoryStore store, ProxyServer server, Map<String, String> messages,
                              BooleanSupplier configReloader) {
        super(store, true, messages, server, configReloader);
    }
}
