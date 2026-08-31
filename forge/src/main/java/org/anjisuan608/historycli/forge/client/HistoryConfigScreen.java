package org.anjisuan608.historycli.forge.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryCliTomlConfigIO;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.forge.HistoryCliForge;

import java.nio.file.Path;

/**
 * Forge 模组列表配置界面：记录命令（开关）、历史上限（输入框）、集成服务器历史（开关），修改持久化到 TOML。
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
        this.configFile = Path.of("config/lichen-history-cli.toml");
        HistoryStore store = HistoryCliForge.clientStore;
        this.record = store != null && store.recordEnabled();
        this.historySize = store != null ? store.maxSize() : 0;
        this.enableIntegrated = HistoryCliForge.enableIntegratedHistory;
    }

    private static HistoryStore historyStore() {
        return HistoryCliForge.clientStore;
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
                    HistoryCliForge.enableIntegratedHistory = enableIntegrated;
                    btn.setMessage(Component.translatable("historycli.config.use_integrated", fmt(enableIntegrated)));
                    saveConfig();
                })
                .bounds(cx - 150, cy + 4, 300, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> {
                    persistSize();
                    onClose();
                })
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
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private void saveConfig() {
        JsonObject root = HistoryCliTomlConfigIO.loadOrCreate(configFile);
        JsonObject client = root.has("client") ? root.getAsJsonObject("client") : new JsonObject();
        client.addProperty("record_history", historyStore() != null && historyStore().recordEnabled());
        client.addProperty("history_size", historySize);
        root.add("client", client);
        root.addProperty("enable_integrated_history", enableIntegrated);
        HistoryCliTomlConfigIO.save(configFile, root);
    }
}
