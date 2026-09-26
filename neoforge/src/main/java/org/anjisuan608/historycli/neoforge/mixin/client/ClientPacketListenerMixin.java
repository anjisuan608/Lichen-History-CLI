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
 *
 * <p>{@code priority = 1500}（高于默认 1000）：需要在其它命令拦截型 mixin 之前运行，
 * 否则顺序未定义，可能导致本 mod 的命令未被记录或 {@code /!!} 被先当成未知命令处理。</p>
 *
 * <p>两条入口都要拦：原版只对 {@code /} 开头的输入调用 {@code sendCommand}，
 * 不带斜杠的裸聊天输入走 {@code sendChat}——只拦前者的话「聊天框直接输入 !! 即展开」对
 * 裸输入不生效。</p>
 */
@Mixin(value = ClientPacketListener.class, priority = 1500)
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

    /**
     * 聊天框直接输入 {@code !!} / {@code !5}（不带前导 {@code /}）时的展开。
     * <p>原版只对 {@code /} 开头的输入调用 {@code sendCommand}，普通聊天走 {@code sendChat}；
     * 不拦这里的话，「聊天框直接输入即展开」实际只会对 {@code /!!} 生效。</p>
     */
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void historycli$expandChat(String message, CallbackInfo ci) {
        if (message == null || message.isEmpty() || !message.startsWith("!")) {
            return;
        }
        String result = HistoryCliNeoForge.interceptClientCommand(message);
        if (result == null) {
            // 无匹配：interceptClientCommand 已经提示过 NO_MATCH
            ci.cancel();
            return;
        }
        if (result.equals(message)) {
            return;   // 不是展开请求（多词的 ! 开头聊天）→ 原样作为聊天发出
        }
        // 展开结果是命令，必须走命令通道而不是聊天通道
        ((ClientPacketListener) (Object) this).sendCommand(result);
        ci.cancel();
    }
}
