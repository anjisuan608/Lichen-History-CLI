package org.anjisuan608.historycli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 配置文件 IO（纯 Java + gson）：生成默认配置并读取。
 * <p>各平台共用：首次启动生成 {@code config/lichen-history-cli.json}，之后读取。
 * 配置结构：{@code enable_integrated_history}（根）+ {@code client}/{@code server} 两个分节
 * （各自含 {@code record_history}、{@code history_size}）。</p>
 *
 * <p>所有 IO/解析失败都会经 {@link Logger} 打出告警，不再静默吞掉——否则用户配置损坏时
 * 只会看到「配置莫名失效」而无从排查。</p>
 */
public final class HistoryCliConfigIO {

    private static final Logger LOG = Logger.getLogger("lichenhistorycli");

    /** 默认配置内容（首次启动生成）。 */
    public static final String DEFAULT_CONFIG =
            "{\n" +
            "  \"enable_integrated_history\": false,\n" +
            "  \"client\": {\n" +
            "    \"record_history\": true,\n" +
            "    \"history_size\": 500\n" +
            "  },\n" +
            "  \"server\": {\n" +
            "    \"record_history\": true,\n" +
            "    \"history_size\": 500\n" +
            "  }\n" +
            "}\n";

    private HistoryCliConfigIO() {
    }

    /**
     * 读取配置；文件不存在时先生成默认配置。
     *
     * @return 根 JsonObject；解析失败时返回空对象（并记录告警）
     */
    public static JsonObject loadOrCreate(Path file) {
        try {
            if (!Files.exists(file)) {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, DEFAULT_CONFIG, StandardCharsets.UTF_8);
                return JsonParser.parseString(DEFAULT_CONFIG).getAsJsonObject();
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.isJsonObject()) {
                throw new IllegalStateException("config root is not a JSON object");
            }
            return root;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load config " + file + ", falling back to defaults", e);
            return new JsonObject();
        }
    }

    /** 读取布尔配置：{@code section} 为 null 时读根，否则读嵌套分节。 */
    public static boolean getBool(JsonObject root, String section, String key, boolean def) {
        try {
            JsonObject target = target(root, section);
            if (target != null && target.has(key)) {
                return target.get(key).getAsBoolean();
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Bad boolean for " + key + ": " + e);
        }
        return def;
    }

    /** 读取整数配置：{@code section} 为 null 时读根，否则读嵌套分节。 */
    public static int getInt(JsonObject root, String section, String key, int def) {
        try {
            JsonObject target = target(root, section);
            if (target != null && target.has(key)) {
                return target.get(key).getAsInt();
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Bad integer for " + key + ": " + e);
        }
        return def;
    }

    private static JsonObject target(JsonObject root, String section) {
        if (section == null || section.isEmpty()) {
            return root;
        }
        return root.has(section) ? root.getAsJsonObject(section) : null;
    }
}
