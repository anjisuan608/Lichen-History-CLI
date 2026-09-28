package org.anjisuan608.historycli.paper;

import org.anjisuan608.historycli.bukkit.BukkitHistoryPlugin;
import org.bukkit.command.CommandSender;

/**
 * Paper / Folia 原生插件入口（由 {@code paper-plugin.yml} 声明，Paper 用自己的加载器加载，
 * 因此在 Paper 上<b>不再被识别为 Bukkit 插件</b>——{@code /plugins} 会把它列在
 * <b>Paper Plugins</b> 分组下，而不是 Bukkit Plugins）。
 *
 * <p>相对 bukkit 版的差异：</p>
 * <ol>
 *   <li><b>命令注册走 Brigadier</b>——{@code paper-plugin.yml} 没有 {@code commands} 字段，
 *       只能经 {@code LifecycleEvents.COMMANDS} 注册；顺带把布尔结果映射成 1/0，
 *       让 {@code /execute} 这类自动化能感知失败（Bukkit 路径恒返回 true）；</li>
 *   <li><b>异步定时落盘</b>：每分钟把脏行写盘，进程被 kill 不再丢失本次会话记录；</li>
 *   <li><b>Adventure 可点击列表行</b>：点击把重跑命令填入输入框。</li>
 * </ol>
 *
 * <p>除这三项之外的全部逻辑（配置、语言、权限门、事件录制）都来自 {@link BukkitHistoryPlugin}
 * ——两个模块编译同一份源码，不复制第二份。</p>
 *
 * <p><b>本类不得出现任何 Paper 类型</b>：Brigadier/Adventure/生命周期的代码全部在
 * {@link PaperSupport} 里，探测在 {@link PaperProbe}。这不是洁癖——实测证明 JVM 在
 * <b>类校验阶段</b>就会解析方法体内 {@code LifecycleEvents.COMMANDS} 这类引用的类型，
 * 一旦放到本类，jar 被误装到 CraftBukkit/Spigot 时会在类加载期抛
 * {@code NoClassDefFoundError}，连「请改用 bukkit 版」的提示都来不及打印。
 * 本类只在 {@link PaperProbe} 确认是 Paper <b>之后</b>才委托调用 {@link PaperSupport}，
 * 使后者只在 Paper 上被加载。</p>
 */
public final class PaperHistoryPlugin extends BukkitHistoryPlugin {

    /** {@code io.papermc.paper.threadedregions.scheduler.ScheduledTask}，用 Object 承接以隔离类型。 */
    private Object flushTask;

    @Override
    public void onEnable() {
        if (!PaperProbe.available()) {
            getLogger().severe("This jar is the Paper/Folia build. "
                    + "On CraftBukkit/Spigot install historycli-bukkit-*.jar instead; "
                    + "the two jars share one plugin name and cannot be installed together.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        super.onEnable();
        // super.onEnable 里 store 初始化失败时为 null，startFlush 内部会处理
        flushTask = PaperSupport.startFlush(this, historyStore());
        if (flushTask != null) {
            getLogger().info("Paper build: Brigadier commands + clickable rows + async flush every 1m");
        }
    }

    /**
     * 覆盖声明式注册：{@code paper-plugin.yml} 不支持 {@code commands} 字段，
     * 改由 {@link PaperSupport} 用 Brigadier 生命周期注册。两个回调都用方法引用，
     * 保证本类的字节码里不含任何 Paper 类型。
     */
    @Override
    protected void registerCommands() {
        PaperSupport.registerCommands(this, this::dispatchToHandler, this::suggest);
    }

    @Override
    public void onDisable() {
        // 只有真正启动过定时任务才去加载 PaperSupport（理由见类注释）
        if (flushTask != null) {
            PaperSupport.cancelFlush(flushTask);
            flushTask = null;
        }
        super.onDisable();
    }

    @Override
    protected void sendLine(CommandSender sender, String text) {
        PaperSupport.sendLine(sender, text);
    }

    @Override
    protected void sendRowLine(CommandSender sender, int index, String text) {
        PaperSupport.sendRow(sender, index, text);
    }
}
