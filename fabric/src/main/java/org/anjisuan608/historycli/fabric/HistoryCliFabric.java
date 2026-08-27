package org.anjisuan608.historycli.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.server.ServerHistoryCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class HistoryCliFabric implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            Path file = FabricLoader.getInstance().getGameDir().resolve("local/historycli/command_history.log");
            serverStore = new HistoryStore(file);
            serverStore.setMaxSize(0);
            serverStore.read();
            LOGGER.info("Lichen History CLI (Fabric server) loaded, history at {}", file);
        }
        CommandRegistrationCallback.EVENT.register(ServerHistoryCommand::register);
    }
}
