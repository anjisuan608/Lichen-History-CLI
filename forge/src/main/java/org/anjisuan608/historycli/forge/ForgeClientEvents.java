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
}
