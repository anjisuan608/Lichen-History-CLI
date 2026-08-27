package org.anjisuan608.historycli.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.anjisuan608.historycli.HistoryStore;

import java.nio.file.Path;

public final class HistoryCliFabricClient implements ClientModInitializer {

    public static HistoryStore store;

    @Override
    public void onInitializeClient() {
        Path file = FabricLoader.getInstance().getGameDir().resolve("command_history.txt");
        store = new HistoryStore(file);
        store.setMaxSize(0); // 跟随原版语义，不额外设上限
        store.read();

        ClientCommandRegistrationCallback.EVENT.register(ClientHistoryCommand::register);
    }
}
