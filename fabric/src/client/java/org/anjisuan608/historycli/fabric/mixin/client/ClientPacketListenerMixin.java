package org.anjisuan608.historycli.fabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.anjisuan608.historycli.HistoryCommandHandler;
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
 *
 * <p>只有「单 token 的 bang 请求」（{@link HistoryCommandHandler#isBangRequest}）才拦截：
 * 聊天框里以 {@code !} 开头的话很多（{@code !gg}、{@code !hello world}），那是聊天，
 * 不得吞掉；以 {@code !} 开头的命令也一律不入史（见 AGENTS §6）。</p>
 */
@Mixin(value = ClientPacketListener.class, priority = 1500)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void historycli$expandCommand(String command, CallbackInfo ci) {
        if (command == null || command.isEmpty()) {
            return;
        }
        if (!command.startsWith("!")) {
            // 普通命令：记录到本 mod 自持日志（store 是懒初始化的，必须判空，
            // 否则 mixin 内 NPE 会直接打断命令发送）
            org.anjisuan608.historycli.HistoryStore store =
                    org.anjisuan608.historycli.fabric.client.HistoryCliFabricClient.store;
            if (store != null) {
                store.add(command);
            }
            return;
        }
        if (!HistoryCommandHandler.isBangRequest(command)) {
            // `!foo bar` 这类多词输入不是展开请求：原样放行，且按约定不入史
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
     * 不拦这里的话，「聊天框直接输入即展开」实际只会对 {@code /!!} 生效。</p>
     */
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void historycli$expandChat(String message, CallbackInfo ci) {
        if (!HistoryCommandHandler.isBangRequest(message)) {
            return;   // 普通聊天（含 !gg、!hello world、孤立 !）原样发出
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
        // 用 LocalPlayer#sendSystemMessage，不用 Gui：`Gui.getChat()`（≤26.1）与 `Gui.hud`（26.2+）
        // 两版互斥，引用任一个都会在另一版编译失败 / 运行期 NoSuchFieldError。
        // sendSystemMessage 在 26.1.2 与 26.2 都存在，且内部各自走正确的门面
        // （前者 getChatListener()、后者 gui.chatListener()），由 Mojang 保证行为一致。
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(
                    Component.translatable(HistoryCommandHandler.Keys.NO_MATCH, raw));
        }
        ci.cancel();
        return true;
    }
}
