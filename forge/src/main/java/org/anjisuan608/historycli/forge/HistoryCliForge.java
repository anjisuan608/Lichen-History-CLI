package org.anjisuan608.historycli.forge;

import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.forge.server.ForgeServerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod("lichenhistorycli")
public final class HistoryCliForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;

    public HistoryCliForge() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        if (event.getCommandSelection() == Commands.CommandSelection.INTEGRATED) {
            return;
        }
        if (serverStore == null) {
            Path file = Path.of("local/historycli/command_history.log");
            serverStore = new HistoryStore(file);
            serverStore.setMaxSize(0);
            serverStore.read();
            LOGGER.info("Lichen History CLI (Forge server) loaded, history at {}", file);
        }
        ForgeServerCommand.register(event.getDispatcher(), event.getBuildContext());
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
}
