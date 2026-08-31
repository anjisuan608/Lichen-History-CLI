package org.anjisuan608.historycli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置文件 IO（纯 Java + gson）：生成默认配置并读取。
 * <p>各平台共用：首次启动生成 {@code config/lichen-history-cli.json}，之后读取。
 * 配置结构：{@code enable_integrated_history}（根）+ {@code client}/{@code server} 两个分节
 * （各自含 {@code record_history}、{@code history_size}）。</p>
 */
public final class HistoryCliConfigIO {

    /** 默认配置内容（首次启动生成）。 */
    public static final String DEFAULT_CONFIG =
            "{\n" +
            "  \"enable_integrated_history\": false,\n" +
            "  \"client\": {\n" +
            "    \"record_history\": true,\n" +
            "    \"history_size\": 0\n" +
            "  },\n" +
            "  \"server\": {\n" +
            "    \"record_history\": true,\n" +
            "    \"history_size\": 0\n" +
            "  }\n" +
            "}\n";

    private HistoryCliConfigIO() {
    }

    /**
     * 读取配置；文件不存在时先生成默认配置。
     *
     * @return 根 JsonObject；解析失败时返回空对象
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
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
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
