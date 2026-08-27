package org.anjisuan608.historycli.fabric.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod Menu 配置界面：显示并切换「记录命令」选项，修改会持久化到配置文件。
 */
public final class HistoryConfigScreen extends Screen {

    private final Screen parent;
    private boolean record;

    protected HistoryConfigScreen(Screen parent) {
        super(Component.translatable("historycli.config.title"));
        this.parent = parent;
        this.record = historyStore() != null && historyStore().recordEnabled();
    }

    private static HistoryStore historyStore() {
        return HistoryCliFabricClient.store;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                Component.translatable("historycli.config.record", record ? "on" : "off"),
                btn -> {
                    record = !record;
                    if (historyStore() != null) {
                        historyStore().setRecordEnabled(record);
                    }
                    saveConfig();
                    Minecraft.getInstance().setScreenAndShow(new HistoryConfigScreen(parent));
                })
                .bounds(this.width / 2 - 100, this.height / 2 - 24, 200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> onClose())
                .bounds(this.width / 2 - 100, this.height / 2 + 8, 200, 20)
                .build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private static void saveConfig() {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("lichen-history-cli.json");
            JsonObject root;
            JsonObject client;
            if (Files.exists(file)) {
                root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                client = root.has("client") ? root.getAsJsonObject("client") : new JsonObject();
            } else {
                root = new JsonObject();
                client = new JsonObject();
                root.add("client", client);
            }
            client.addProperty("record_history", HistoryCliFabricClient.store != null && HistoryCliFabricClient.store.recordEnabled());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
