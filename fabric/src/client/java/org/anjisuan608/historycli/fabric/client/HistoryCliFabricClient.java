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
        Path file = FabricLoader.getInstance().getGameDir().resolve("local/historycli/command_history.log");
        file.getParent().toFile().mkdirs();
        store = new HistoryStore(file);
        store.setMaxSize(0); // 本 mod 自持日志，不设额外上限
        store.read();
        FabricConfigHelper.apply(store);

        ClientCommandRegistrationCallback.EVENT.register(ClientHistoryCommand::register);
    }
}
