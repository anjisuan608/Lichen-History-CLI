package org.anjisuan608.historycli.fabric.client;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.HistoryCliFabric;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod Menu 配置界面：记录命令（开关）、历史上限（输入框）、集成服务器历史（开关），修改持久化。
 */
public final class HistoryConfigScreen extends Screen {

    private final Screen parent;
    private final Path configFile;
    private boolean record;
    private boolean enableIntegrated;
    private int historySize;
    private EditBox sizeBox;

    public HistoryConfigScreen(Screen parent) {
        super(Component.translatable("historycli.config.title"));
        this.parent = parent;
        this.configFile = FabricLoader.getInstance().getConfigDir().resolve("lichen-history-cli.json");
        HistoryStore store = HistoryCliFabricClient.store;
        if (store != null) {
            this.record = store.recordEnabled();
            this.historySize = store.maxSize();
        } else {
            // 存储尚未创建时，以磁盘配置为准，避免把默认值 0/false 写回配置
            com.google.gson.JsonObject root = org.anjisuan608.historycli.HistoryCliConfigIO.loadOrCreate(configFile);
            this.record = org.anjisuan608.historycli.HistoryCliConfigIO.getBool(root, "client", "record_history", true);
            this.historySize = org.anjisuan608.historycli.HistoryCliConfigIO.getInt(root, "client", "history_size", 500);
        }
        this.enableIntegrated = HistoryCliFabric.enableIntegratedHistory;
    }

    private static HistoryStore historyStore() {
        return HistoryCliFabricClient.store;
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
                    btn.setMessage(Component.translatable("historycli.config.record", fmt(record)));
                    saveConfig();
                })
                .bounds(cx - 150, cy - 52, 300, 20)
                .build());

        sizeBox = new EditBox(this.font, cx - 150, cy - 24, 300, 20,
                Component.translatable("historycli.config.history_size_label"));
        sizeBox.setMaxLength(7);
        sizeBox.setValue(String.valueOf(historySize));
        addRenderableWidget(sizeBox);

        addRenderableWidget(Button.builder(
                Component.translatable("historycli.config.use_integrated", fmt(enableIntegrated)),
                btn -> {
                    enableIntegrated = !enableIntegrated;
                    HistoryCliFabric.enableIntegratedHistory = enableIntegrated;
                    btn.setMessage(Component.translatable("historycli.config.use_integrated", fmt(enableIntegrated)));
                    saveConfig();
                })
                .bounds(cx - 150, cy + 4, 300, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> onClose())
                .bounds(cx - 100, cy + 36, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.text(this.font,
                Component.translatable("historycli.config.history_size_label").getString(),
                cx - 150, cy - 34, 0xFFFFFFFF);
    }

    private void persistSize() {
        if (sizeBox == null) {
            return;
        }
        try {
            int parsed = Integer.parseInt(sizeBox.getValue().trim());
            historySize = Math.max(0, parsed);
            if (historyStore() != null) {
                historyStore().setMaxSize(historySize);
            }
            saveConfig();
        } catch (NumberFormatException e) {
            sizeBox.setValue(String.valueOf(historySize));
        }
    }

    private static String fmt(boolean b) {
        return b ? "on" : "off";
    }

    @Override
    public void onClose() {
        // ESC 关闭也要落盘，否则输入框里的改动会被静默丢弃
        persistSize();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private void saveConfig() {
        try {
            JsonObject root;
            JsonObject client;
            if (Files.exists(configFile)) {
                root = com.google.gson.JsonParser.parseString(Files.readString(configFile, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("client") && root.get("client").isJsonObject()) {
                    client = root.getAsJsonObject("client");
                } else {
                    client = new JsonObject();
                    root.add("client", client); // 缺分节时必须挂回去，否则本次修改会被整体丢弃
                }
            } else {
                root = new JsonObject();
                client = new JsonObject();
                root.add("client", client);
            }
            client.addProperty("record_history", record);
            client.addProperty("history_size", historySize);
            root.addProperty("enable_integrated_history", enableIntegrated);
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(configFile, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            org.anjisuan608.historycli.fabric.HistoryCliFabric.LOGGER.warn("Failed to save config {}", configFile, e);
        }
    }
}
