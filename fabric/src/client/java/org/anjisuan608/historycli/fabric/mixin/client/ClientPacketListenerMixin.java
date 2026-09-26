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
 *
 * <p>{@code priority = 1500}（高于默认 1000）：本注入需要在 Fabric 客户端命令 API 之前运行，
 * 否则顺序未定义，可能导致本 mod 的命令未被记录或 {@code /!!} 被当成未知命令先处理。</p>
 */
@Mixin(value = ClientPacketListener.class, priority = 1500)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void historycli$expandCommand(String command, CallbackInfo ci) {
        if (command == null || command.isEmpty()) {
            return;
        }
        if (!command.startsWith("!")) {
            // 普通命令：记录到本 mod 自持日志
            org.anjisuan608.historycli.fabric.client.HistoryCliFabricClient.store.add(command);
            return;
        }
        String expanded = ClientHistoryExpander.expand(command);
        if (!historycli$rejectIfBang(command, expanded, ci)) {
            ((ClientPacketListener) (Object) this).sendCommand(
                    expanded.startsWith("/") ? expanded.substring(1) : expanded);
            ci.cancel();
        }
    }

    /**
     * 聊天框直接输入 {@code !!} / {@code !5}（不带前导 {@code /}）时的展开。
     * <p>原版只对 {@code /} 开头的输入调用 {@code sendCommand}，普通聊天走 {@code sendChat}；
     * 不拦这里的话，文档承诺的「聊天框直接输入即展开」实际只会对 {@code /!!} 生效。</p>
     */
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void historycli$expandChat(String message, CallbackInfo ci) {
        if (message == null || message.isEmpty() || !message.startsWith("!")) {
            return;
        }
        String expanded = ClientHistoryExpander.expand(message);
        if (!historycli$rejectIfBang(message, expanded, ci)) {
            // 展开结果是命令，必须走命令通道而不是聊天通道
            ((ClientPacketListener) (Object) this).sendCommand(
                    expanded.startsWith("/") ? expanded.substring(1) : expanded);
            ci.cancel();
        }
    }

    /**
     * @return true 表示已拒绝（展开失败或展开结果仍是 ! 形式）并取消原调用；
     *         false 表示 {@code expanded} 可安全执行。
     */
    private static boolean historycli$rejectIfBang(String raw, String expanded, CallbackInfo ci) {
        if (expanded != null && !expanded.startsWith("!")) {
            return false;
        }
        // 无匹配，或匹配到历史中字面量 `!!` 行（继续展开会无限递归）
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.hud != null) {
            mc.gui.hud.getChat().addClientSystemMessage(
                    Component.translatable(org.anjisuan608.historycli.HistoryCommandHandler.Keys.NO_MATCH, raw));
        }
        ci.cancel();
        return true;
    }
}
