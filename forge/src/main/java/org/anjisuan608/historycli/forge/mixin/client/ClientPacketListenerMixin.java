package org.anjisuan608.historycli.forge.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.anjisuan608.historycli.forge.HistoryCliForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 客户端**唯一的**命令发送钩子。
 *
 * <p>为什么必须用 mixin（字节码已核实）：</p>
 * <ul>
 *   <li>{@code ClientPacketListener.sendCommand} 在 Forge 补丁版里的第一条指令是
 *       {@code ClientCommandHandler.runCommand(String)}，随后直接发包，<b>不触发任何事件</b>；</li>
 *   <li>{@code ForgeHooksClient.onClientSendMessage}（触发 {@code ClientChatEvent}）
 *       只出现在 {@code sendChat} 里，也就是只有聊天文本会走事件；</li>
 *   <li>{@code net.minecraftforge.client.event} 中带 Command 的类只有 {@code RegisterClientCommandsEvent}。</li>
 * </ul>
 * <p>结果就是：不加这个 mixin，Forge 客户端敲的 {@code /命令} 永远进不了历史，客户端历史恒为空。</p>
 *
 * <p>只拦 {@code sendCommand}：聊天侧（裸 {@code !!}）已由 {@link org.anjisuan608.historycli.forge.ForgeClientEvents}
 * 的 {@code ClientChatEvent} 覆盖，两边都拦会重复处理。</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void historycli$expand(String command, CallbackInfo ci) {
        String result = HistoryCliForge.interceptClientCommand(command);
        if (result == null) {
            // 无匹配：interceptClientCommand 已提示 NO_MATCH，取消发送
            ci.cancel();
            return;
        }
        if (!result.equals(command)) {
            // `!!` 展开结果不同：改以展开后的命令发送
            ((ClientPacketListener) (Object) this).sendCommand(result);
            ci.cancel();
        }
    }
}
