package org.anjisuan608.historycli.fabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.fabric.client.ClientHistoryExpander;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截客户端命令发送，处理 /! 系列展开（!! / !n / !-n / !string）。
 * 仅操作客户端本地历史，与服务端是否安装 mod 无关。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void historycli$expand(String command, CallbackInfo ci) {
        if (command == null || command.isEmpty()) {
            return;
        }
        if (!command.startsWith("!")) {
            // 普通命令：记录到本 mod 自持日志
            org.anjisuan608.historycli.fabric.client.HistoryCliFabricClient.store.add(command);
            return;
        }
        String expanded = ClientHistoryExpander.expand(command);
        if (expanded == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null && mc.gui.hud != null) {
                mc.gui.hud.getChat().addClientSystemMessage(
                        Component.literal("No matching history entry for '" + command + "'"));
            }
            ci.cancel();
            return;
        }
        ((ClientPacketListener) (Object) this).sendCommand(expanded.startsWith("/") ? expanded.substring(1) : expanded);
        ci.cancel();
    }
}
