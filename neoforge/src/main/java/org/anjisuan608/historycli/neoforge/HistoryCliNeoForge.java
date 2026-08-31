package org.anjisuan608.historycli.neoforge;

import com.google.gson.JsonObject;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.anjisuan608.historycli.HistoryCliConfigIO;
import org.anjisuan608.historycli.HistoryCliTomlConfigIO;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.neoforge.server.NeoForgeServerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod("lichenhistorycli")
public final class HistoryCliNeoForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;
    public static HistoryStore clientStore;
    public static boolean enableIntegratedHistory = false;

    private static boolean serverRecordEnabled = true;
    private static int serverHistorySize = 0;
    private static boolean clientRecordEnabled = true;
    private static int clientHistorySize = 0;

    public HistoryCliNeoForge(net.neoforged.fml.ModContainer modContainer) {
        readConfig();
        NeoForge.EVENT_BUS.register(this);
        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
            modContainer.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (mc, parent) -> new org.anjisuan608.historycli.neoforge.client.HistoryConfigScreen(parent));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (clientStore != null) {
                    clientStore.write();
                }
            }, "LichenHistoryCLI-Client-Save"));
        }
    }

    @SubscribeEvent
    public void onRegisterClientCommands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        initClientStore();
        org.anjisuan608.historycli.neoforge.client.NeoForgeClientCommand.register(event.getDispatcher(), event.getBuildContext());
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
            if (expanded == null) {
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
                store.setMaxSize(serverHistorySize);
                store.setRecordEnabled(serverRecordEnabled);
                store.read();
                serverStore = store;
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

    private static void initServerStore() {
        if (serverStore != null) {
            return;
        }
        Path file = Path.of("local/historycli/command_history.log");
        file.getParent().toFile().mkdirs();
        HistoryStore store = new HistoryStore(file);
        store.setMaxSize(serverHistorySize);
        store.setRecordEnabled(serverRecordEnabled);
        store.read();
        serverStore = store;
        LOGGER.info("Lichen History CLI (NeoForge server) loaded, history at {}", file);
    }

    private static void initClientStore() {
        if (clientStore != null) {
            return;
        }
        Path file = Path.of("local/historycli/command_history.log");
        file.getParent().toFile().mkdirs();
        HistoryStore store = new HistoryStore(file);
        store.setMaxSize(clientHistorySize);
        store.setRecordEnabled(clientRecordEnabled);
        store.read();
        clientStore = store;
    }

    private void readConfig() {
        JsonObject root = HistoryCliTomlConfigIO.loadOrCreate(Path.of("config/lichen-history-cli.toml"));
        enableIntegratedHistory = HistoryCliConfigIO.getBool(root, null, "enable_integrated_history", false);
        serverRecordEnabled = HistoryCliConfigIO.getBool(root, "server", "record_history", true);
        serverHistorySize = HistoryCliConfigIO.getInt(root, "server", "history_size", 0);
        clientRecordEnabled = HistoryCliConfigIO.getBool(root, "client", "record_history", true);
        clientHistorySize = HistoryCliConfigIO.getInt(root, "client", "history_size", 0);
    }
}
