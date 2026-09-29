package org.anjisuan608.historycli.neoforge;

import com.google.gson.JsonObject;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.anjisuan608.historycli.HistoryCliConfigIO;
import org.anjisuan608.historycli.HistoryCliTomlConfigIO;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.neoforge.server.NeoForgeServerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * NeoForge 主类：配置读取、服务端/集成服务器历史存储、命令事件录入。
 * <p>客户端事件统一在 {@link NeoForgeClientEvents}（{@code @EventBusSubscriber(Dist.CLIENT)}）中注册，
 * 以免专用服务器解析客户端类型时 {@link NoClassDefFoundError}。</p>
 */
@Mod("lichenhistorycli")
public final class HistoryCliNeoForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;
    public static HistoryStore clientStore;
    public static boolean enableIntegratedHistory = false;

    private static boolean serverRecordEnabled = true;
    private static int serverHistorySize = 500;
    private static boolean clientRecordEnabled = true;
    private static int clientHistorySize = 500;

    public HistoryCliNeoForge(net.neoforged.fml.ModContainer modContainer) {
        readConfig();
        NeoForge.EVENT_BUS.register(this);
        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
            // 客户端专用逻辑整体下沉到 NeoForgeClientHooks：@Mod 类的常量池里不能出现任何客户端类型，
            // 否则专用服务器在构造 @Mod 类时就会因解析 Screen 直接崩（Forge 侧真机实测踩过）。
            org.anjisuan608.historycli.neoforge.client.NeoForgeClientHooks.registerClient(modContainer);
        }
    }

    // 客户端的 ! 展开拦截已移至 client.NeoForgeClientHooks#interceptClientCommand：
    // 服务端可达的 @Mod 类里不能出现 Minecraft/Screen 等客户端类型（否则专用服务器加载即崩）。

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        Commands.CommandSelection selection = event.getCommandSelection();
        boolean integrated = selection == Commands.CommandSelection.INTEGRATED;
        if (integrated && !enableIntegratedHistory) {
            return;
        }
        if (integrated) {
            NeoForgeServerCommand.register(event.getDispatcher(), event.getBuildContext(), true);
            return;
        }
        initServerStore();
        NeoForgeServerCommand.register(event.getDispatcher(), event.getBuildContext(), false);
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        // 每条命令（含原版命令）都会先过这里：异常一旦逃逸，原版只回一句 command.failed、
        // 且日志里不留堆栈，表现出来就是"全局所有命令都执行出错"（Forge 侧真机实测踩过）。
        try {
            if (serverStore == null || event.getParseResults() == null) {
                return;
            }
            String cmd = event.getParseResults().getReader().getString();
            if (cmd == null || cmd.isEmpty() || cmd.startsWith("!")) {
                return;
            }
            serverStore.add(cmd);
        } catch (Throwable t) {
            LOGGER.error("[lichenhistorycli] CommandEvent listener threw", t);
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (server == null || !server.isSingleplayer()) {
            return;   // 专用服务器的 store 在 RegisterCommandsEvent 里已建好
        }
        // 换存档 / 关掉集成历史：先写盘并丢弃旧 store，
        // 否则 serverStore 仍指向世界 A 的文件，世界 B 的命令会写进世界 A 的日志
        dropStaleServerStore();
        if (!enableIntegratedHistory) {
            return;
        }
        try {
            Path worldData = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("lichenhistorycli");
            worldData.toFile().mkdirs();
            HistoryStore store = new HistoryStore(worldData.resolve("command_history.log"));
            serverStore = store;
            applyServerConfig();
            store.read();
            LOGGER.info("Lichen History CLI (integrated server) history at {}", worldData.resolve("command_history.log"));
        } catch (Exception e) {
            LOGGER.warn("Failed to init integrated history", e);
        }
    }

    /** 写盘并丢弃当前的集成服务器 store（换存档/关闭集成历史时调用）。 */
    private static void dropStaleServerStore() {
        if (serverStore == null) {
            return;
        }
        try {
            serverStore.write();
        } catch (Exception e) {
            LOGGER.warn("Failed to flush previous integrated history", e);
        }
        serverStore = null;
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (serverStore != null) {
            serverStore.write();
            LOGGER.info("Lichen History CLI (NeoForge server) history saved");
        }
    }

    private static void applyServerConfig() {
        if (serverStore != null) {
            serverStore.setMaxSize(serverHistorySize);
            serverStore.setRecordEnabled(serverRecordEnabled);
        }
    }

    private static void applyClientConfig() {
        if (clientStore != null) {
            clientStore.setMaxSize(clientHistorySize);
            clientStore.setRecordEnabled(clientRecordEnabled);
        }
    }

    /** 历史文件路径（游戏目录下），供配置界面等复用，避免相对路径依赖 CWD。 */
    public static Path historyFile() {
        return FMLPaths.GAMEDIR.get().resolve("local").resolve("historycli").resolve("command_history.log");
    }

    /** 配置文件路径（config 目录下），供配置界面等复用，避免相对路径依赖 CWD。 */
    public static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("lichen-history-cli.toml");
    }

    static void initServerStore() {
        if (serverStore != null) {
            return;
        }
        Path file = historyFile();
        file.getParent().toFile().mkdirs();
        HistoryStore store = new HistoryStore(file);
        serverStore = store;
        applyServerConfig();
        readQuietly(store, "server");
        LOGGER.info("Lichen History CLI (NeoForge server) loaded, history at {}", file);
    }

    public static void initClientStore() {
        if (clientStore != null) {
            return;
        }
        Path file = historyFile();
        file.getParent().toFile().mkdirs();
        HistoryStore store = new HistoryStore(file);
        clientStore = store;
        applyClientConfig();
        readQuietly(store, "client");
    }

    /**
     * 读取历史文件；失败时用空历史继续。
     * <p>日志损坏（非原子写崩溃的产物）会让 {@code Files.readAllLines} 抛
     * {@link java.io.UncheckedIOException}，若不接住，mod 会在命令注册阶段直接挂掉。</p>
     */
    private static void readQuietly(HistoryStore store, String side) {
        try {
            store.read();
        } catch (Exception e) {
            LOGGER.warn("Failed to read {} history, starting empty", side, e);
        }
    }

    private void readConfig() {
        applyConfig(HistoryCliTomlConfigIO.loadOrCreate(configFile()));
    }

    private static void applyConfig(JsonObject root) {
        enableIntegratedHistory = HistoryCliConfigIO.getBool(root, null, "enable_integrated_history", false);
        serverRecordEnabled = HistoryCliConfigIO.getBool(root, "server", "record_history", true);
        serverHistorySize = HistoryCliConfigIO.getInt(root, "server", "history_size", 500);
        clientRecordEnabled = HistoryCliConfigIO.getBool(root, "client", "record_history", true);
        clientHistorySize = HistoryCliConfigIO.getInt(root, "client", "history_size", 500);
        applyServerConfig();
        applyClientConfig();
    }

    /**
     * 供 {@code /historycliserver reload} 调用：重新读取配置并应用到历史存储。
     *
     * @return true=已重载；false=配置损坏（保留当前设置，不回落成默认值还报成功）
     */
    public static boolean reloadConfig() {
        JsonObject root = HistoryCliTomlConfigIO.loadOrCreate(configFile());
        if (root.entrySet().isEmpty()) {
            LOGGER.warn("lichen-history-cli.toml could not be parsed, keeping current settings");
            return false;
        }
        applyConfig(root);
        return true;
    }
}
