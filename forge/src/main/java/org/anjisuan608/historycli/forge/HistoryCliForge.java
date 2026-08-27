package org.anjisuan608.historycli.forge;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("lichenhistorycli")
public final class HistoryCliForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public HistoryCliForge(org.slf4j.Logger log) {
        LOGGER.info("Lichen History CLI (Forge) loaded");
    }
}
