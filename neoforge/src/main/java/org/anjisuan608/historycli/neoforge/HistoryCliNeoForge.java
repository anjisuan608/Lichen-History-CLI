package org.anjisuan608.historycli.neoforge;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("lichenhistorycli")
public final class HistoryCliNeoForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    public HistoryCliNeoForge(org.slf4j.Logger log) {
        LOGGER.info("Lichen History CLI (NeoForge) loaded");
    }
}
