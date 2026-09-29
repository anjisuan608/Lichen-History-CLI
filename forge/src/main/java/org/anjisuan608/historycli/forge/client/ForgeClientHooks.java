package org.anjisuan608.historycli.forge.client;

import net.minecraftforge.common.MinecraftForge;
import org.anjisuan608.historycli.HistoryCommandHandler;
import org.anjisuan608.historycli.forge.ForgeClientEvents;
import org.anjisuan608.historycli.forge.HistoryCliForge;

/**
 * 客户端专用钩子：{@code @Mod} 类里所有会牵连客户端类型的东西都必须放在这里。
 *
 * <p><b>本类只能在客户端 dist 被加载。</b>其常量池含有 {@code Screen}、{@code Minecraft}、
 * {@code LocalPlayer} 等客户端类型，专用服务器一旦加载它，就会被 FML 的 RuntimeDistCleaner 当场拒绝
 * （{@code Attempted to load class net/minecraft/client/gui/screens/Screen for invalid dist DEDICATED_SERVER}）。</p>
 *
 * <p>踩坑记录（真机实测）：光把调用写在 {@code if (dist.isClient())} 里<b>不够</b>——
 * {@code @Mod} 类构造器里的配置屏 lambda 会编译成合成方法
 * {@code lambda$new$0(Screen): Screen}，其描述符把 {@code Screen} 写进 {@code @Mod} 类的常量池；
 * FML 在 {@code constructMod} 调 {@code getDeclaredConstructor()} 时就会去解析它，
 * 于是服务端在<b>构造 mod 类的瞬间</b>崩溃。同理，{@code interceptClientCommand} 里直接写
 * {@code Minecraft.getInstance()} 也会把客户端类型带进 {@code @Mod} 类。
 * 结论：<b>服务端可达的类（含 {@code @Mod} 类）里一个客户端类型都不能出现</b>，
 * 无论出现在方法体、lambda 还是字段里。</p>
 */
public final class ForgeClientHooks {

    private ForgeClientHooks() {
    }

    /**
     * 客户端注册（配置屏、客户端事件、停服写盘钩子）。
     *
     * <p>只允许由 {@code HistoryCliForge} 构造器在 {@code FMLEnvironment.dist.isClient()} 为真时调用；
     * 专用服务器上该分支不执行，本类就不会被加载。</p>
     */
    public static void registerClient() {
        MinecraftForge.EVENT_BUS.register(ForgeClientEvents.INSTANCE);
        MinecraftForge.registerConfigScreen(parent -> new HistoryConfigScreen(parent));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (HistoryCliForge.clientStore != null) {
                HistoryCliForge.clientStore.write();
            }
        }, "LichenHistoryCLI-Client-Save"));
    }

    /**
     * 客户端侧命令拦截（mixin 与 {@code ClientChatEvent} 调用），负责 {@code !} 系列展开。
     *
     * @param command 不带前导 {@code /} 的原始输入
     * @return {@code null} 表示取消发送（已给出提示）；非 null 表示以该值替换原输入；
     *         与入参相同表示原样放行
     */
    public static String interceptClientCommand(String command) {
        if (command == null || command.isEmpty()) {
            return command;
        }
        HistoryCliForge.initClientStore();
        if (HistoryCliForge.clientStore == null) {
            return command;
        }
        if (command.startsWith("!")) {
            if (!HistoryCommandHandler.isBangRequest(command)) {
                // `!foo bar` 这种多词输入不视为展开请求：原样放行，并且自己记入历史
                return command;
            }
            String expanded = HistoryCliForge.clientStore.resolve(command);
            if (expanded == null || expanded.startsWith("!")) {
                // 无匹配，或匹配到历史中的字面量 `!!` 行（再次展开会无限递归）
                // 用 LocalPlayer#sendSystemMessage 发提示，不碰 Gui：
                // `Gui.getChat()`（26.1）与 `Gui.hud`（26.2+）两版互斥，直接引用 hud 在 26.1 上编译不过
                // （Fabric 端同坑已修，见 ClientPacketListenerMixin；26.1 为本模组声明的编译下限）。
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    HistoryCommandHandler.Keys.NO_MATCH,
                                    net.minecraft.network.chat.Component.literal(command)));
                }
                return null;
            }
            HistoryCliForge.clientStore.add(expanded);
            return HistoryCommandHandler.stripLeadingSlash(expanded);
        }
        HistoryCliForge.clientStore.add(command);
        return command;
    }
}
