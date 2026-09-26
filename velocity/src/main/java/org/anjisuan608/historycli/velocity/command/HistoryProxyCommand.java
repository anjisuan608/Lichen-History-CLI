package org.anjisuan608.historycli.velocity.command;

import org.anjisuan608.historycli.HistoryStore;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 普通命令 /historyproxy（别名 historypro/historyp）：bash 标准子集（不含 reload 与 ! 系列）。
 * <p>普通命令不会走到 {@code executeCommand}（EXECUTE 动作在 common 层就被拒绝），
 * 因此不传 ProxyServer。</p>
 */
public final class HistoryProxyCommand extends VelocityHistoryCommand {

    public HistoryProxyCommand(HistoryStore store, Map<String, String> messages,
                               BooleanSupplier configReloader) {
        super(store, false, messages, null, configReloader);
    }
}
