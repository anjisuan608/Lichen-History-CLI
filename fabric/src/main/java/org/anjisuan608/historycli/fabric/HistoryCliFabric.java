package org.anjisuan608.historycli.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.server.ServerHistoryCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HistoryCliFabric implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;
    public static boolean enableIntegratedHistory = false;

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER || FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            readConfig();
        }
        CommandRegistrationCallback.EVENT.register(ServerHistoryCommand::register);

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            Path file = FabricLoader.getInstance().getGameDir().resolve("local/historycli/command_history.log");
            serverStore = new HistoryStore(file);
            serverStore.setMaxSize(0);
            serverStore.read();
            LOGGER.info("Lichen History CLI (Fabric server) loaded, history at {}", file);
        }

        ServerLifecycleEvents.SERVER_STARTED.register(HistoryCliFabric::onServerStarted);
    }

    private static void readConfig() {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("lichen-history-cli.json");
            if (Files.exists(file)) {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("enable_integrated_history")) {
                    enableIntegratedHistory = root.get("enable_integrated_history").getAsBoolean();
                }
            }
        } catch (Exception e) {
            // 忽略，使用默认
        }
    }

    private static void onServerStarted(MinecraftServer server) {
        // 集成服务器（单人/局域网）且配置开启时，初始化服务端历史到 world/data
        if (server.isSingleplayer()) {
            if (!enableIntegratedHistory) {
                return;
            }
            try {
                Path worldData = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("lichenhistorycli");
                worldData.toFile().mkdirs();
                HistoryStore store = new HistoryStore(worldData.resolve("command_history.log"));
                store.setMaxSize(0);
                store.read();
                serverStore = store;
                LOGGER.info("Lichen History CLI (integrated server) history at {}", worldData.resolve("command_history.log"));
            } catch (Exception e) {
                LOGGER.warn("Failed to init integrated history", e);
            }
        }
    }
}
