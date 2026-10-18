package org.anjisuan608.historycli.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.HistoryCliFabric;

import java.nio.file.Path;

public final class HistoryCliFabricClient implements ClientModInitializer {

    public static HistoryStore store;

    @Override
    public void onInitializeClient() {
        Path file = FabricLoader.getInstance().getGameDir().resolve("local/historycli/command_history.log");
        file.getParent().toFile().mkdirs();
        store = new HistoryStore(file);
        FabricConfigHelper.apply(store);
        try {
            store.read();
        } catch (Exception e) {
            // 日志损坏（非原子写崩溃的产物）时用空历史继续，而不是让客户端起不来
            HistoryCliFabric.LOGGER.warn("Failed to read client history, starting empty", e);
        }

        ClientCommandRegistrationCallback.EVENT.register(ClientHistoryCommand::register);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // isDirty 短路：没有新行时不必做一次全量快照 + fsync
            if (store != null && store.isDirty()) {
                // Fabric 的事件 invoker **不 catch** 订阅者异常：磁盘满/只读时
                // UncheckedIOException 会直接崩掉客户端，而本该只是"正常断开"。
                try {
                    store.write();
                } catch (Throwable t) {
                    HistoryCliFabric.LOGGER.error("Failed to save client history on disconnect", t);
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (store != null) {
                try {
                    store.write();
                } catch (Throwable t) {
                    HistoryCliFabric.LOGGER.error("Failed to save client history on shutdown", t);
                }
            }
        }, "LichenHistoryCLI-Client-Save"));
    }
}
