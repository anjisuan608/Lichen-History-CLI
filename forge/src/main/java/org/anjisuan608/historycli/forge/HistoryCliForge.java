package org.anjisuan608.historycli.forge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.forge.server.ForgeServerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod("lichenhistorycli")
public final class HistoryCliForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;
    public static boolean enableIntegratedHistory = false;

    public HistoryCliForge() {
        readConfig();
        MinecraftForge.EVENT_BUS.register(this);
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
        if (serverStore == null) {
            Path file = Path.of("local/historycli/command_history.log");
            serverStore = new HistoryStore(file);
            serverStore.setMaxSize(0);
            serverStore.read();
            LOGGER.info("Lichen History CLI (Forge server) loaded, history at {}", file);
        }
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
                store.setMaxSize(0);
                store.read();
                serverStore = store;
                LOGGER.info("Lichen History CLI (integrated server) history at {}", worldData.resolve("command_history.log"));
            } catch (Exception e) {
                LOGGER.warn("Failed to init integrated history", e);
            }
        }
    }

    private static void readConfig() {
        try {
            Path file = Path.of("config/lichen-history-cli.json");
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
}
