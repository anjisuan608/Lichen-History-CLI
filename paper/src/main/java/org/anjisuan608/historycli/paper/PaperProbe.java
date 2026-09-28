package org.anjisuan608.historycli.paper;

/**
 * 判断当前服务器是否带 Paper 的 API。
 *
 * <p>刻意<b>不引用任何 Paper 类型</b>——只用 {@link Class.forName} 探测。这样
 * {@link PaperHistoryPlugin} 即使被装到 CraftBukkit/Spigot 上，也能先打出
 * 「请改用 bukkit 版 jar」的明确提示再自我禁用，而不是在类校验阶段就抛
 * {@code NoClassDefFoundError}，让运维看不出所以然。</p>
 */
final class PaperProbe {

    private PaperProbe() {
    }

    /** Paper 与 Folia 都有这个调度器类型；Spigot/CraftBukkit 没有。 */
    static boolean available() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
