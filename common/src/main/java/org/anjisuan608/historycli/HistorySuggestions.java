package org.anjisuan608.historycli;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 命令参数补齐（tab 补全）：打完主命令后建议可用的子命令/选项。
 * 供各平台（fabric/neoforge/forge）的 Brigadier 命令树使用。
 */
public final class HistorySuggestions {

    private static final List<String> BASE = List.of(
            "list", "-c", "-w", "-a", "-r", "-d", "reload", "help", "?", "!!");

    private HistorySuggestions() {
    }

    /**
     * 返回一个建议提供器，基于已输入内容（remaining）过滤候选参数。
     */
    public static <S> SuggestionProvider<S> suggest() {
        return (ctx, builder) -> {
            String input = builder.getRemaining();
            for (String c : BASE) {
                if (c.startsWith(input)) {
                    builder.suggest(c);
                }
            }
            return builder.buildFuture();
        };
    }
}
