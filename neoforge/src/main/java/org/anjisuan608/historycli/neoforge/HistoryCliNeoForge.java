package org.anjisuan608.historycli.neoforge;

import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.neoforge.server.NeoForgeServerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod("lichenhistorycli")
public final class HistoryCliNeoForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public static HistoryStore serverStore;

    public HistoryCliNeoForge() {
        NeoForge.EVENT_BUS.register(this);
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
            LOGGER.info("Lichen History CLI (NeoForge server) loaded, history at {}", file);
        }
        NeoForgeServerCommand.register(event.getDispatcher(), event.getBuildContext());
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
