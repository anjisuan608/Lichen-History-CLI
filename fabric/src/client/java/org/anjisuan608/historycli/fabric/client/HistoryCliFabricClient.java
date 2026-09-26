package org.anjisuan608.historycli.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.anjisuan608.historycli.HistoryStore;

import java.nio.file.Path;

public final class HistoryCliFabricClient implements ClientModInitializer {

    public static HistoryStore store;

    @Override
    public void onInitializeClient() {
        Path file = FabricLoader.getInstance().getGameDir().resolve("local/historycli/command_history.log");
        file.getParent().toFile().mkdirs();
        store = new HistoryStore(file);
        FabricConfigHelper.apply(store);
        store.read();

        ClientCommandRegistrationCallback.EVENT.register(ClientHistoryCommand::register);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (store != null) {
                store.write();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (store != null) {
                store.write();
            }
        }, "LichenHistoryCLI-Client-Save"));
    }
}
