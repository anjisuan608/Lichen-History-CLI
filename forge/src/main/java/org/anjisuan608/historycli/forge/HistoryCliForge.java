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
import org.anjisuan608.historycli.HistoryCliConfigIO;
import org.anjisuan608.historycli.HistoryCliTomlConfigIO;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.forge.server.ForgeServerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod("lichenhistorycli")
public final class HistoryCliForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;
    public static HistoryStore clientStore;
    public static boolean enableIntegratedHistory = false;

    private static boolean serverRecordEnabled = true;
    private static int serverHistorySize = 0;
    private static boolean clientRecordEnabled = true;
    private static int clientHistorySize = 0;

    public HistoryCliForge(net.minecraftforge.fml.ModContainer modContainer) {
        readConfig();
        MinecraftForge.EVENT_BUS.register(this);
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            modContainer.registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            parent -> new org.anjisuan608.historycli.forge.client.HistoryConfigScreen(parent)));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (clientStore != null) {
                    clientStore.write();
                }
            }, "LichenHistoryCLI-Client-Save"));
        }
    }

    @SubscribeEvent
    public void onRegisterClientCommands(net.minecraftforge.client.event.RegisterClientCommandsEvent event) {
        initClientStore();
        org.anjisuan608.historycli.forge.client.ForgeClientCommand.register(event.getDispatcher(), event.getBuildContext());
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
            LOGGER.info("Lichen History CLI (Forge server) history saved");
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
        LOGGER.info("Lichen History CLI (Forge server) loaded, history at {}", file);
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
