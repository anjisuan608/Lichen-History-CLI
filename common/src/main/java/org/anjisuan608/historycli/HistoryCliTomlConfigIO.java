package org.anjisuan608.historycli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TOML 配置 IO（纯 Java）：为 Forge/NeoForge 生成与解析 {@code config/lichen-history-cli.toml}。
 * <p>解析结果以 {@link JsonObject} 表示（分节映射为嵌套对象），从而复用 {@link HistoryCliConfigIO}
 * 的 {@code getBool}/{@code getInt} 访问器。仅支持本项目配置所需的简单 TOML 子集：
 * 根键值对、{@code [section]} 分节、布尔/整数/字符串值、{@code #} 注释。</p>
 */
public final class HistoryCliTomlConfigIO {

    /** 默认 TOML 配置内容（首次启动生成）。 */
    public static final String DEFAULT_CONFIG =
            "# Lichen History CLI 配置\n" +
            "# record_history: 是否记录新命令；history_size: 历史上限（0 = 不限）\n" +
            "enable_integrated_history = false\n" +
            "\n" +
            "[client]\n" +
            "record_history = true\n" +
            "history_size = 0\n" +
            "\n" +
            "[server]\n" +
            "record_history = true\n" +
            "history_size = 0\n";

    private HistoryCliTomlConfigIO() {
    }

    /**
     * 读取 TOML 配置；文件不存在时先生成默认配置。
     *
     * @return 根 JsonObject（分节为嵌套对象）；解析失败时返回空对象
     */
    public static JsonObject loadOrCreate(Path file) {
        try {
            if (!Files.exists(file)) {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, DEFAULT_CONFIG, StandardCharsets.UTF_8);
                return parse(DEFAULT_CONFIG);
            }
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    /** 解析简单 TOML（根键值对 + 分节）为 JsonObject。 */
    static JsonObject parse(String toml) {
        JsonObject root = new JsonObject();
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        String current = null;
        for (String rawLine : toml.split("\r?\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (current == null) {
                root.add(key, toPrimitive(value));
            } else {
                sections.computeIfAbsent(current, k -> new LinkedHashMap<>()).put(key, value);
            }
        }
        for (Map.Entry<String, Map<String, String>> sec : sections.entrySet()) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, String> kv : sec.getValue().entrySet()) {
                obj.add(kv.getKey(), toPrimitive(kv.getValue()));
            }
            root.add(sec.getKey(), obj);
        }
        return root;
    }

    /**
     * 将根对象写回 TOML（标量在前，分节在后），保留解析结果中的全部字段。
     */
    public static void save(Path file, JsonObject root) {
        try {
            StringBuilder sb = new StringBuilder("# Lichen History CLI 配置\n");
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    sb.append(e.getKey()).append(" = ").append(toTomlValue(e.getValue())).append('\n');
                }
            }
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (e.getValue().isJsonObject()) {
                    sb.append('\n').append('[').append(e.getKey()).append("]\n");
                    for (Map.Entry<String, JsonElement> kv : e.getValue().getAsJsonObject().entrySet()) {
                        if (!kv.getValue().isJsonObject()) {
                            sb.append(kv.getKey()).append(" = ").append(toTomlValue(kv.getValue())).append('\n');
                        }
                    }
                }
            }
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static String toTomlValue(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsString();
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsString();
        }
        String s = value.getAsString();
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String stripComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (ch == '#' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static JsonElement toPrimitive(String value) {
        String v = value.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            return JsonParser.parseString(v); // 带引号的字符串，交给 gson 处理转义
        }
        if (v.equals("true") || v.equals("false")) {
            return JsonParser.parseString(v);
        }
        if (v.matches("-?\\d+")) {
            return JsonParser.parseString(v);
        }
        return JsonParser.parseString("\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
    }
}
