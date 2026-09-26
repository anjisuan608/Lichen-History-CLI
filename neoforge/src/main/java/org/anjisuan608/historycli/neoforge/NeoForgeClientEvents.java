package org.anjisuan608.historycli.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;

/**
 * 客户端专属事件的订阅者。
 *
 * <p>拆成独立类、并只在客户端 dist 下注册（见 {@link HistoryCliNeoForge} 构造函数）：
 * 专用服务器加载公共 {@code @Mod} 类时若用反射扫描到带客户端事件参数的方法，
 * 会去解析服务端不存在的 {@code net.neoforged.neoforge.client.event.*} 类型，
 * 进而 {@link NoClassDefFoundError}。</p>
 *
 * <p>用「实例 + 条件注册」而不是 {@code @EventBusSubscriber} 注解，是为了不依赖注解的
 * bus 默认值等版本差异——只用到 {@code NeoForge.EVENT_BUS.register(Object)} 这一稳定 API。</p>
 */
public final class NeoForgeClientEvents {

    public static final NeoForgeClientEvents INSTANCE = new NeoForgeClientEvents();

    private NeoForgeClientEvents() {
    }

    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        HistoryCliNeoForge.initClientStore();
        org.anjisuan608.historycli.neoforge.client.NeoForgeClientCommand.register(
                event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        if (HistoryCliNeoForge.clientStore != null) {
            HistoryCliNeoForge.clientStore.write();
            HistoryCliNeoForge.LOGGER.info("Lichen History CLI (NeoForge client) history saved on disconnect");
        }
    }

    @SubscribeEvent
    public void onClientStopped(ClientStoppedEvent event) {
        if (HistoryCliNeoForge.clientStore != null) {
            HistoryCliNeoForge.clientStore.write();
            HistoryCliNeoForge.LOGGER.info("Lichen History CLI (NeoForge client) history saved on stop");
        }
    }
}
