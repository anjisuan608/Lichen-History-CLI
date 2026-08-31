package org.anjisuan608.historycli.fabric.mixin.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.dedicated.DedicatedServer;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.HistoryCliFabric;
import org.anjisuan608.historycli.fabric.server.ServerHistoryExpander;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截服务端控制台输入：记录非 ! 命令到历史，并处理 ! 系列展开（!! / !n / !-n / !string）。
 * 展开后以服务端命令源执行；无匹配时记录日志并取消。
 */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {

    @Inject(method = "handleConsoleInput", at = @At("HEAD"), cancellable = true)
    private void historycli$expand(String msg, CommandSourceStack source, CallbackInfo ci) {
        if (msg == null || msg.isEmpty()) {
            return;
        }
        if (!msg.startsWith("!")) {
            HistoryStore store = HistoryCliFabric.serverStore;
            if (store != null) {
                store.add(msg);
            }
            return;
        }
        String expanded = ServerHistoryExpander.expand(msg);
        if (expanded != null) {
            ((DedicatedServer) (Object) this).handleConsoleInput(expanded, source);
            ci.cancel();
        } else {
            HistoryCliFabric.LOGGER.warn("No matching history entry for '{}'", msg);
            ci.cancel();
        }
    }
}
