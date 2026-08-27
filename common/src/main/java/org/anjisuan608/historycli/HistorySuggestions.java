package org.anjisuan608.historycli;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 命令参数补齐（tab 补全）：打完主命令后建议可用的子命令/选项。
 * 供各平台（fabric/neoforge/forge）的 Brigadier 命令树使用。
 * 完整命令建议含 reload / -d / !!，普通命令仅 bash 标准子集（不含 ! 系列）。
 */
public final class HistorySuggestions {

    private static final List<String> FULL = List.of(
            "list", "-c", "-w", "-a", "-r", "-d", "reload", "help", "?", "!!");
    private static final List<String> PLAIN = List.of(
            "list", "-c", "-w", "-a", "-r", "help", "?");

    private HistorySuggestions() {
    }

    /** 返回建议提供器；allowFull=true 时建议完整子命令（含 !!），否则普通子集。 */
    public static <S> SuggestionProvider<S> suggest(boolean allowFull) {
        List<String> candidates = allowFull ? FULL : PLAIN;
        return (ctx, builder) -> {
            String input = builder.getRemaining();
            for (String c : candidates) {
                if (c.startsWith(input)) {
                    builder.suggest(c);
                }
            }
            return builder.buildFuture();
        };
    }
}
