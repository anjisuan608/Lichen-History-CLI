package org.anjisuan608.historycli.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryStore;

/**
 * Mod Menu 配置界面：显示并切换「记录命令」选项。
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
}
