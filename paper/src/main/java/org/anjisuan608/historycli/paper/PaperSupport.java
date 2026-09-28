package org.anjisuan608.historycli.paper;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.anjisuan608.historycli.HistoryParser;
import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.bukkit.BukkitHistoryPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 所有 Paper / Folia 专属能力都集中在本类，供 {@link PaperHistoryPlugin} 委托调用。
 *
 * <p><b>为什么必须集中</b>：{@code PaperHistoryPlugin} 是插件入口，它的类会在
 * <b>非 Paper</b> 服务器上被加载并<b>校验</b>。实测证明仅把 Paper 类型移出方法描述符不够——
 * 验证阶段就会解析 {@code LifecycleEvents.COMMANDS} 这类字段/方法引用的类型
 * （曾因此在 Spigot 上抛 {@code NoClassDefFoundError: LifecycleEventType}）。
 * 因此本类只在 {@link PaperProbe} 确认是 Paper <b>之后</b>才会被调用，
 * 从而只在 Paper 上被加载并链接。</p>
 */
final class PaperSupport {

    private PaperSupport() {
    }

    /** 子命令分派回调（布尔语义与 {@code BukkitHistoryPlugin#dispatchToHandler} 一致）。 */
    @FunctionalInterface
    interface CommandRunner {
        boolean run(CommandSender sender, String[] args, boolean allowFull);
    }

    /** Tab 补全回调（复用 bukkit 版逻辑，保证两端提示一致）。 */
    @FunctionalInterface
    interface SuggestRunner {
        List<String> suggest(CommandSender sender, String[] args, boolean allowFull);
    }

    /**
     * 经 Paper 的 Brigadier 生命周期注册命令。
     *
     * <p>{@code paper-plugin.yml} <b>没有 {@code commands} 字段</b>，命令只能这样注册。
     * 用 {@code LifecycleEventManager} 的好处是它会在每次需要时（含 {@code /reload}）
     * 重新注册，不必自己处理重载时序。</p>
     */
    static void registerCommands(BukkitHistoryPlugin plugin, CommandRunner runner, SuggestRunner suggester) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(node("historycliserver", runner, suggester, true),
                    "Full history server command", List.of("historyclis", "historycliser"));
            registrar.register(node("historyserver", runner, suggester, false),
                    "History server command (bash subset)", List.of("historys", "historyser"));
        });
    }

    /** 「根字面量 + 可选贪婪参数」；贪婪参数承载任意子命令文本。 */
    private static LiteralCommandNode<CommandSourceStack> node(String name, CommandRunner runner,
                                                               SuggestRunner suggester, boolean allowFull) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(name)
                .executes(ctx -> run(ctx, runner, new String[0], allowFull))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, sb) -> complete(ctx, sb, suggester, allowFull))
                        .executes(ctx -> run(ctx, runner,
                                HistoryParser.split(StringArgumentType.getString(ctx, "args"))
                                        .toArray(new String[0]), allowFull)));
        return builder.build();
    }

    /** 把布尔结果映射成 Brigadier 的 1（成功）/ 0（失败），让 {@code /execute} 能感知失败。 */
    private static int run(CommandContext<CommandSourceStack> ctx, CommandRunner runner,
                           String[] args, boolean allowFull) {
        CommandSender sender = ctx.getSource().getSender();
        return runner.run(sender, args, allowFull) ? 1 : 0;
    }

    /**
     * Tab 补全：委托给 bukkit 版的 {@code suggest}（含权限判定与前缀过滤），
     * 只是把结果挂到「正在输入的那个 token」的区间上——贪婪参数的默认区间覆盖整段剩余文本，
     * 不偏移的话 {@code historycliserver list } 按 TAB 会把已输入的内容整段替换掉。
     */
    private static CompletableFuture<Suggestions> complete(CommandContext<CommandSourceStack> ctx,
                                                           SuggestionsBuilder builder,
                                                           SuggestRunner suggester, boolean allowFull) {
        CommandSender sender = ctx.getSource().getSender();
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String typed = lastSpace < 0 ? remaining : remaining.substring(lastSpace + 1);
        String already = lastSpace < 0 ? "" : remaining.substring(0, lastSpace + 1);

        List<String> args = new ArrayList<>(HistoryParser.split(already));
        args.add(typed); // 正在输入的 token，可能为空串

        SuggestionsBuilder sub = builder.createOffset(builder.getStart() + lastSpace + 1);
        for (String option : suggester.suggest(sender, args.toArray(new String[0]), allowFull)) {
            sub.suggest(option);
        }
        return sub.buildFuture();
    }

    /**
     * 每分钟把未落盘的行异步写入文件（节奏与代理端一致）。
     *
     * <p>这是相对 Bukkit 版的核心增益：Bukkit 版只在 {@code onDisable} 写盘，
     * 进程被 kill 会丢掉整个会话的新增记录；这里用 Paper 的
     * {@code AsyncScheduler} 在<b>不阻塞任何区域线程</b>的前提下定期落盘，
     * 因此在 Paper 与 Folia 上都安全——不能用 {@code Bukkit.getScheduler()}，
     * 那个在 Folia 上直接不可用。</p>
     *
     * @return 可用于取消的任务句柄（以 {@code Object} 返回，避免 Paper 类型泄漏到调用方）
     */
    static Object startFlush(Plugin plugin, HistoryStore store) {
        if (store == null) {
            return null;
        }
        return plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> {
            try {
                if (store.isDirty()) {
                    store.write();
                }
            } catch (Throwable t) {
                // 定时任务里的异常不能冒泡（会被调度器吞掉并可能停掉任务），记日志即可
                plugin.getLogger().warning("Periodic history flush failed: " + t);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    /** 取消定时落盘（传入 {@code null} 或非 Paper 任务句柄时静默忽略）。 */
    static void cancelFlush(Object task) {
        if (task instanceof ScheduledTask scheduled) {
            scheduled.cancel();
        }
    }

    /** 通过 Adventure 发送普通消息（保留 Paper 的组件能力，与 Bukkit 版渲染等价）。 */
    static void sendLine(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text));
    }

    /**
     * 列表行：可点击，点击后把「重跑第 N 条」的命令填入输入框。
     *
     * <p>用 {@link ClickEvent#suggestCommand} 而不是 {@code runCommand}——历史里可能有
     * {@code /stop}、{@code /op} 这类破坏性命令，自动执行太危险；填入输入框让用户自己按回车。</p>
     */
    static void sendRow(CommandSender sender, int index, String text) {
        String rerun = "/historycliserver !" + index;
        sender.sendMessage(Component.text()
                .append(Component.text(text))
                .clickEvent(ClickEvent.suggestCommand(rerun))
                .hoverEvent(HoverEvent.showText(Component.text(rerun)))
                .build());
    }
}
