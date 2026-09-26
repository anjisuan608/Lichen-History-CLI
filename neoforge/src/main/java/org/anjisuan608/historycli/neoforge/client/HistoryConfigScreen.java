package org.anjisuan608.historycli.neoforge.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryCliTomlConfigIO;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.neoforge.HistoryCliNeoForge;

import java.nio.file.Path;

/**
 * NeoForge 模组列表配置界面：记录命令（开关）、历史上限（输入框）、集成服务器历史（开关），修改持久化到 TOML。
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
        this.configFile = HistoryCliNeoForge.configFile();
        HistoryStore store = HistoryCliNeoForge.clientStore;
        if (store != null) {
            this.record = store.recordEnabled();
            this.historySize = store.maxSize();
        } else {
            // 存储尚未创建（如从模组列表直接打开）时以磁盘配置为准，
            // 否则会把默认值 false/0 在下次保存时写回配置。
            JsonObject root = HistoryCliTomlConfigIO.loadOrCreate(configFile);
            this.record = org.anjisuan608.historycli.HistoryCliConfigIO.getBool(root, "client", "record_history", true);
            this.historySize = org.anjisuan608.historycli.HistoryCliConfigIO.getInt(root, "client", "history_size", 500);
        }
        this.enableIntegrated = HistoryCliNeoForge.enableIntegratedHistory;
    }

    private static HistoryStore historyStore() {
        return HistoryCliNeoForge.clientStore;
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
                    HistoryCliNeoForge.enableIntegratedHistory = enableIntegrated;
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
        JsonObject root = HistoryCliTomlConfigIO.loadOrCreate(configFile);
        JsonObject client = root.has("client") && root.get("client").isJsonObject()
                ? root.getAsJsonObject("client") : new JsonObject();
        // 以界面上的开关状态为准：存储未创建时读取 recordEnabled() 会得到 false 并写坏配置
        client.addProperty("record_history", record);
        client.addProperty("history_size", historySize);
        root.add("client", client);
        root.addProperty("enable_integrated_history", enableIntegrated);
        HistoryCliTomlConfigIO.save(configFile, root);
    }
}
