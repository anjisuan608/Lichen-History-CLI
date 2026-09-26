package org.anjisuan608.historycli.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.anjisuan608.historycli.HistoryCliConfigIO;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.server.ServerHistoryCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class HistoryCliFabric implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;
    public static boolean enableIntegratedHistory = false;

    /** 服务端配置（[server] 节），启动读取、reload 时重新读取。 */
    public static boolean serverRecordEnabled = true;
    public static int serverHistorySize = 500;
    /** 客户端配置（[client] 节），由客户端模块读取；此处仅统一保存以便 reload 后同步。 */
    public static boolean clientRecordEnabled = true;
    public static int clientHistorySize = 500;

    @Override
    public void onInitialize() {
        readConfig();
        CommandRegistrationCallback.EVENT.register(ServerHistoryCommand::register);

        if (FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.SERVER) {
            Path file = FabricLoader.getInstance().getGameDir().resolve("local/historycli/command_history.log");
            serverStore = new HistoryStore(file);
            applyServerConfig();
            serverStore.read();
            LOGGER.info("Lichen History CLI (Fabric server) loaded, history at {}", file);
        }

        ServerLifecycleEvents.SERVER_STARTED.register(HistoryCliFabric::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(HistoryCliFabric::onServerStopping);
    }

    private static void onServerStopping(MinecraftServer server) {
        if (serverStore != null) {
            serverStore.write();
            LOGGER.info("Lichen History CLI (Fabric server) history saved");
        }
    }

    /** 读取配置文件到内存（不应用到存储）。 */
    private static com.google.gson.JsonObject readConfigFile() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("lichen-history-cli.json");
        return HistoryCliConfigIO.loadOrCreate(file);
    }

    private static void readConfig() {
        applyConfig(readConfigFile());
    }

    private static void applyConfig(com.google.gson.JsonObject root) {
        boolean previousIntegrated = enableIntegratedHistory;
        enableIntegratedHistory = HistoryCliConfigIO.getBool(root, null, "enable_integrated_history", false);
        serverRecordEnabled = HistoryCliConfigIO.getBool(root, "server", "record_history", true);
        serverHistorySize = HistoryCliConfigIO.getInt(root, "server", "history_size", 500);
        clientRecordEnabled = HistoryCliConfigIO.getBool(root, "client", "record_history", true);
        clientHistorySize = HistoryCliConfigIO.getInt(root, "client", "history_size", 500);
        applyServerConfig();
        if (previousIntegrated != enableIntegratedHistory) {
            LOGGER.info("enable_integrated_history changed; the command tree is registered at startup, restart to apply");
        }
    }

    private static void applyServerConfig() {
        if (serverStore != null) {
            serverStore.setRecordEnabled(serverRecordEnabled);
            serverStore.setMaxSize(serverHistorySize);
        }
    }

    /**
     * 供 {@code /historycliserver reload} 调用：重新读取配置并应用到历史存储。
     *
     * @return 恒为 true（本平台支持重载）
     */
    public static boolean reloadConfig() {
        applyConfig(readConfigFile());
        return true;
    }

    private static void onServerStarted(MinecraftServer server) {
        // 集成服务器（单人/局域网）且配置开启时，初始化服务端历史到 world/data
        if (server.isSingleplayer()) {
            if (!enableIntegratedHistory) {
                return;
            }
            try {
                Path worldData = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("data").resolve("lichenhistorycli");
                worldData.toFile().mkdirs();
                HistoryStore store = new HistoryStore(worldData.resolve("command_history.log"));
                serverStore = store;
                applyServerConfig();
                store.read();
                LOGGER.info("Lichen History CLI (integrated server) history at {}",
                        worldData.resolve("command_history.log"));
            } catch (Exception e) {
                LOGGER.warn("Failed to init integrated history", e);
            }
        }
    }
}
