package org.anjisuan608.historycli.fabric.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.HistoryCliFabric;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod Menu 配置界面：展示全部配置项（记录命令 / 历史上限 / 集成服务器历史），修改会持久化。
 */
public final class HistoryConfigScreen extends Screen {

    private final Screen parent;
    private boolean record;
    private int historySize;
    private boolean enableIntegrated;
    private Path configFile;

    protected HistoryConfigScreen(Screen parent) {
        super(Component.translatable("historycli.config.title"));
        this.parent = parent;
        this.configFile = FabricLoader.getInstance().getConfigDir().resolve("lichen-history-cli.json");
        loadConfig();
        this.record = historyStore() != null && historyStore().recordEnabled();
        this.historySize = historyStore() != null ? historyStore().maxSize() : 0;
        this.enableIntegrated = HistoryCliFabric.enableIntegratedHistory;
    }

    private static HistoryStore historyStore() {
        return HistoryCliFabricClient.store;
    }

    private void loadConfig() {
        try {
            if (Files.exists(configFile)) {
                JsonObject root = JsonParser.parseString(Files.readString(configFile, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("enable_integrated_history")) {
                    HistoryCliFabric.enableIntegratedHistory = root.get("enable_integrated_history").getAsBoolean();
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        addRenderableWidget(Button.builder(
                Component.translatable("historycli.config.record", fmt(record)),
                btn -> {
                    record = !record;
                    if (historyStore() != null) {
                        historyStore().setRecordEnabled(record);
                    }
                    saveConfig();
                    Minecraft.getInstance().setScreenAndShow(new HistoryConfigScreen(parent));
                })
                .bounds(cx - 150, cy - 52, 300, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.translatable("historycli.config.history_size", historySize),
                btn -> {
                    historySize = historySize <= 0 ? 100 : 0;
                    if (historyStore() != null) {
                        historyStore().setMaxSize(historySize);
                    }
                    saveConfig();
                    Minecraft.getInstance().setScreenAndShow(new HistoryConfigScreen(parent));
                })
                .bounds(cx - 150, cy - 20, 300, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.translatable("historycli.config.use_integrated", fmt(enableIntegrated)),
                btn -> {
                    enableIntegrated = !enableIntegrated;
                    HistoryCliFabric.enableIntegratedHistory = enableIntegrated;
                    saveConfig();
                    Minecraft.getInstance().setScreenAndShow(new HistoryConfigScreen(parent));
                })
                .bounds(cx - 150, cy + 12, 300, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> onClose())
                .bounds(cx - 100, cy + 44, 200, 20)
                .build());
    }

    private static String fmt(boolean b) {
        return b ? "on" : "off";
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private void saveConfig() {
        try {
            JsonObject root;
            JsonObject client;
            if (Files.exists(configFile)) {
                root = JsonParser.parseString(Files.readString(configFile, StandardCharsets.UTF_8)).getAsJsonObject();
                client = root.has("client") ? root.getAsJsonObject("client") : new JsonObject();
            } else {
                root = new JsonObject();
                client = new JsonObject();
                root.add("client", client);
            }
            client.addProperty("record_history", historyStore() != null && historyStore().recordEnabled());
            client.addProperty("history_size", historySize);
            root.addProperty("enable_integrated_history", enableIntegrated);
            Files.writeString(configFile, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
