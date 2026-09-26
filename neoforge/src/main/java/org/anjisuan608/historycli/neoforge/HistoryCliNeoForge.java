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
            NeoForge.EVENT_BUS.register(NeoForgeClientEvents.INSTANCE);
            modContainer.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (mc, parent) -> new org.anjisuan608.historycli.neoforge.client.HistoryConfigScreen(parent));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (clientStore != null) {
                    clientStore.write();
                }
            }, "LichenHistoryCLI-Client-Save"));
        }
    }

    /**
     * 客户端命令发送拦截（由 mixin 调用）：普通命令记录；! 系列展开。
     *
     * @return null 表示取消发送；返回值与入参不同表示以返回值替换发送；相同表示照常发送
     */
    public static String interceptClientCommand(String command) {
        if (command == null || command.isEmpty()) {
            return command;
        }
        initClientStore();
        if (clientStore == null) {
            return command;
        }
        if (command.startsWith("!")) {
            String expanded = clientStore.resolve(command);
            if (expanded == null || expanded.startsWith("!")) {
                // 无匹配，或匹配到历史中的字面量 `!!` 行（再次展开会无限递归）
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.gui != null && mc.gui.hud != null) {
                    mc.gui.hud.getChat().addClientSystemMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    org.anjisuan608.historycli.HistoryCommandHandler.Keys.NO_MATCH,
                                    net.minecraft.network.chat.Component.literal(command)));
                }
                return null;
            }
            clientStore.add(expanded);
            return org.anjisuan608.historycli.HistoryCommandHandler.stripLeadingSlash(expanded);
        }
        clientStore.add(command);
        return command;
    }

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
        if (serverStore == null || event.getParseResults() == null) {
            return;
        }
        String cmd = event.getParseResults().getReader().getString();
        if (cmd == null || cmd.isEmpty() || cmd.startsWith("!")) {
            return;
        }
        serverStore.add(cmd);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (server != null && server.isSingleplayer() && enableIntegratedHistory) {
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
        store.read();
        LOGGER.info("Lichen History CLI (NeoForge server) loaded, history at {}", file);
    }

    static void initClientStore() {
        if (clientStore != null) {
            return;
        }
        Path file = historyFile();
        file.getParent().toFile().mkdirs();
        HistoryStore store = new HistoryStore(file);
        clientStore = store;
        applyClientConfig();
        store.read();
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
     * @return 恒为 true
     */
    public static boolean reloadConfig() {
        applyConfig(HistoryCliTomlConfigIO.loadOrCreate(configFile()));
        return true;
    }
}
