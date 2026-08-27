package org.anjisuan608.historycli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 配置模型（纯 Java，各平台从自身配置加载后填充）。
 * 对应三模式接管（none/file/full）、auto_load、history_size、cleanup_bang_lines。
 */
public final class HistoryCliConfig {

    /** 接管模式。 */
    public enum TakeoverMode {
        NONE, FILE, FULL;

        public static TakeoverMode parse(String value) {
            if (value == null) {
                return NONE;
            }
            switch (value.toLowerCase(Locale.ROOT)) {
                case "file":
                    return FILE;
                case "full":
                    return FULL;
                default:
                    return NONE;
            }
        }
    }

    private TakeoverMode takeover = TakeoverMode.NONE;
    private boolean autoLoad = true;
    private int historySize = 500;
    private boolean cleanupBangLines = true;

    public TakeoverMode takeover() {
        return takeover;
    }

    public HistoryCliConfig takeover(TakeoverMode takeover) {
        this.takeover = takeover;
        return this;
    }

    public boolean autoLoad() {
        return autoLoad;
    }

    public HistoryCliConfig autoLoad(boolean autoLoad) {
        this.autoLoad = autoLoad;
        return this;
    }

    public int historySize() {
        return historySize;
    }

    public HistoryCliConfig historySize(int historySize) {
        this.historySize = historySize;
        return this;
    }

    public boolean cleanupBangLines() {
        return cleanupBangLines;
    }

    public HistoryCliConfig cleanupBangLines(boolean cleanupBangLines) {
        this.cleanupBangLines = cleanupBangLines;
        return this;
    }

    public static HistoryCliConfig defaults() {
        return new HistoryCliConfig();
    }
}
