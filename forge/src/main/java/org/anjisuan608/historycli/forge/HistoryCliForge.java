package org.anjisuan608.historycli.forge;

import com.google.gson.JsonObject;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.anjisuan608.historycli.HistoryCliConfigIO;
import org.anjisuan608.historycli.HistoryCliTomlConfigIO;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.forge.server.ForgeServerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Forge 主类：配置读取、服务端/集成服务器历史存储、命令事件录入。
 * <p>客户端事件统一交由 {@link ForgeClientEvents}，仅在客户端 dist 注册，
 * 以免专用服务器解析客户端类型时 {@link NoClassDefFoundError}。</p>
 */
@Mod("lichenhistorycli")
public final class HistoryCliForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;
    public static HistoryStore clientStore;
    public static boolean enableIntegratedHistory = false;

    private static boolean serverRecordEnabled = true;
    private static int serverHistorySize = 500;
    private static boolean clientRecordEnabled = true;
    private static int clientHistorySize = 500;

    public HistoryCliForge(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        readConfig();
        MinecraftForge.EVENT_BUS.register(this);
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.register(ForgeClientEvents.INSTANCE);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (clientStore != null) {
                    clientStore.write();
                }
            }, "LichenHistoryCLI-Client-Save"));
        }
    }

    /**
     * 客户端发送路径拦截：普通命令记录；! 系列展开。
     *
     * @return null 表示取消发送；返回值与入参不同表示以返回值替换；相同表示照常发送
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
                                    HistoryCommandHandler.Keys.NO_MATCH,
                                    net.minecraft.network.chat.Component.literal(command)));
                }
                return null;
            }
            clientStore.add(expanded);
            return HistoryCommandHandler.stripLeadingSlash(expanded);
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
            ForgeServerCommand.register(event.getDispatcher(), event.getBuildContext(), true);
            return;
        }
        initServerStore();
        ForgeServerCommand.register(event.getDispatcher(), event.getBuildContext(), false);
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
            LOGGER.info("Lichen History CLI (Forge server) history saved");
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

    /** 历史文件路径（游戏目录），供配置界面复用，避免相对路径依赖 CWD。 */
    public static Path historyFile() {
        return FMLPaths.GAMEDIR.get().resolve("local").resolve("historycli").resolve("command_history.log");
    }

    /** 配置文件路径（config 目录），供配置界面复用，避免相对路径依赖 CWD。 */
    public static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("lichen-history-cli.toml");
    }

    private static void initServerStore() {
        if (serverStore != null) {
            return;
        }
        Path file = historyFile();
        file.getParent().toFile().mkdirs();
        HistoryStore store = new HistoryStore(file);
        serverStore = store;
        applyServerConfig();
        store.read();
        LOGGER.info("Lichen History CLI (Forge server) loaded, history at {}", file);
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
