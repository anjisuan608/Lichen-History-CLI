package org.anjisuan608.historycli.fabric.mixin.server;

import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.HistoryCliFabric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截玩家通过聊天栏发送的 / 命令，录入服务端历史。
 * 分别注入 handleChatCommand（未签名）和 handleSignedChatCommand（签名）两条路径。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    /**
     * 录入历史。
     * <p>以 {@code !} 开头的输入是「未被展开的历史展开请求」（例如玩家敲 {@code /!!}），
     * 它在本平台不会被执行，因此不入史；否则历史里会出现字面量 {@code !!} 行，
     * 之后 {@code !!} 展开会命中它。代价：以 {@code !} 开头的**真实**命令也不会被记录（见 AGENTS.md 已知限制）。</p>
     */
    private void historycli$record(String command) {
        if (command == null || command.isEmpty() || command.startsWith("!")) {
            return;
        }
        HistoryStore store = HistoryCliFabric.serverStore;
        if (store != null) {
            store.add(command);
        }
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"))
    private void historycli$recordUnsignedCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        historycli$record(packet.command());
    }

    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"))
    private void historycli$recordSignedCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
        historycli$record(packet.command());
    }
}
