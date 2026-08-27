package org.anjisuan608.historycli.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HistoryCliFabric implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("lichenhistorycli");

    @Override
    public void onInitialize() {
        LOGGER.info("Lichen History CLI (Fabric) loaded");
    }
}
