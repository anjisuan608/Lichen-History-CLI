package org.anjisuan608.historycli.neoforge.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.anjisuan608.historycli.neoforge.HistoryCliNeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截客户端命令发送，处理 /! 系列展开（!! / !n / !-n / !string）并记录命令。
 * NeoForge 26.2 移除了发送路径上的所有事件（ClientChatEvent 不再触发），必须使用 mixin。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void historycli$expand(String command, CallbackInfo ci) {
        String result = HistoryCliNeoForge.interceptClientCommand(command);
        if (result == null) {
            ci.cancel();
            return;
        }
        if (!result.equals(command)) {
            ((ClientPacketListener) (Object) this).sendCommand(result);
            ci.cancel();
        }
    }
}
