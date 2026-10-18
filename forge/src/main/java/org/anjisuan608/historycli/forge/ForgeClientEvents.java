package org.anjisuan608.historycli.forge;

import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

/**
 * 客户端专属事件的订阅者。
 *
 * <p>拆成独立类、并只在 {@code Dist.CLIENT} 下注册（见 {@link HistoryCliForge} 构造函数）：
 * 专用服务器加载公共 {@code @Mod} 类时若用反射扫描到带客户端事件参数的方法，
 * 会去解析服务端不存在的客户端类型，进而 {@link NoClassDefFoundError}。</p>
 *
 * <p>用「实例 + 条件注册」而不是 {@code @EventBusSubscriber} 注解，是为了不依赖注解的
 * bus 默认值等版本差异——只用到 {@code MinecraftForge.EVENT_BUS.register(Object)} 这一稳定 API。</p>
 */
public final class ForgeClientEvents {

    public static final ForgeClientEvents INSTANCE = new ForgeClientEvents();

    private ForgeClientEvents() {
    }

    /**
     * 客户端发送路径拦截。
     *
     * <p>经反编译确认：{@code ClientPacketListener.sendChat} 会调用
     * {@code ForgeHooksClient.onClientSendMessage} 从而触发本事件，而 {@code sendCommand} <b>不会</b>。
     * 因此本事件只覆盖「非 / 前缀」的输入：</p>
     * <ul>
     *   <li>单 token 的 {@code !!} / {@code !n} / {@code !string} → 展开后以命令发送，
     *       并抑制原聊天输入；</li>
     *   <li>多词的 {@code !} 开头输入（{@code !gg}、{@code !hello world}）或孤立 {@code !}
     *       → 那是<b>聊天</b>，原样放行，不能吞掉；</li>
     *   <li>以 {@code /} 开头 → 仅记录，不干预；</li>
     *   <li>普通聊天文本 → <b>不记录</b>。历史只存命令，聊天内容属隐私，不能混进来。</li>
     * </ul>
     */
    @SubscribeEvent
    public void onClientChat(net.minecraftforge.client.event.ClientChatEvent event) {
        String message = event.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }
        if (message.startsWith("!")) {
            if (!org.anjisuan608.historycli.HistoryCommandHandler.isBangRequest(message)) {
                return;   // 不是展开请求 → 原样作为聊天发出
            }
            String expanded = org.anjisuan608.historycli.forge.client.ForgeClientHooks.interceptClientCommand(message);
            // 抑制原输入（展开失败时也不要把字面量 `!!` 当聊天发出去）
            event.setMessage("");
            if (expanded != null && !expanded.isEmpty()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null && mc.player.connection != null) {
                    mc.player.connection.sendCommand(expanded);
                }
            }
            return;
        }
        if (message.startsWith("/")) {
            // 记录命令（去掉前导 /），其余原样放行
            org.anjisuan608.historycli.forge.client.ForgeClientHooks.interceptClientCommand(message.substring(1));
        }
    }

    @SubscribeEvent
    public void onRegisterClientCommands(net.minecraftforge.client.event.RegisterClientCommandsEvent event) {
        HistoryCliForge.initClientStore();
        org.anjisuan608.historycli.forge.client.ForgeClientCommand.register(
                event.getDispatcher(), event.getBuildContext());
    }

    /**
     * 断线即写盘：Forge 侧原本只有 JVM shutdown hook，「断线后停在主菜单、随后进程被 kill」
     * 会丢掉本次会话的全部新增历史；NeoForge 侧是 LoggingOut + ClientStopped 双保险、
     * Fabric 是 DISCONNECT 即写，这里补齐成同一口径。
     *
     * <p>事件类型可用性已核：{@code ClientPlayerNetworkEvent$LoggingOut} 在 Forge
     * {@code 26.1.2-64.1.3} 与 {@code 26.3-66.0.6} 的 universal jar 中都存在。
     * 本类只在客户端 dist 被 {@link org.anjisuan608.historycli.forge.client.ForgeClientHooks}
     * 注册，专用服务器不会加载它。</p>
     */
    @SubscribeEvent
    public void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (HistoryCliForge.clientStore != null) {
            try {
                HistoryCliForge.clientStore.write();
                HistoryCliForge.LOGGER.info("Lichen History CLI (Forge client) history saved on disconnect");
            } catch (Throwable t) {
                HistoryCliForge.LOGGER.error("Failed to save client history on disconnect", t);
            }
        }
    }
}
