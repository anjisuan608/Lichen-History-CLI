package org.anjisuan608.historycli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TOML 配置 IO（纯 Java）：为 Forge/NeoForge 生成与解析 {@code config/lichen-history-cli.toml}。
 * <p>解析结果以 {@link JsonObject} 表示（分节映射为嵌套对象），从而复用 {@link HistoryCliConfigIO}
 * 的 {@code getBool}/{@code getInt} 访问器。</p>
 *
 * <p>支持的 TOML 子集：根键值对、{@code [section]} 分节（含带引号的分节名）、布尔/整数/浮点/
 * 字符串（含转义）、数组、{@code #} 注释。<b>不支持</b>多行字符串（{@code """}）与内联表——
 * 遇到多行字符串时 {@link #save} 会原样保留该行，不覆盖用户内容。</p>
 *
 * <p>{@link #save} 采用「按 key 合并回原文」的方式写回：注释、空行、用户自行添加的键全部保留，
 * 只替换受管理键的值。</p>
 */
public final class HistoryCliTomlConfigIO {

    private static final Logger LOG = Logger.getLogger("lichenhistorycli");

    /** 默认 TOML 配置内容（首次启动生成）。 */
    public static final String DEFAULT_CONFIG =
            "# Lichen History CLI 配置\n" +
            "# record_history: 是否记录新命令；history_size: 历史上限（0 = 不限）\n" +
            "enable_integrated_history = false\n" +
            "\n" +
            "[client]\n" +
            "record_history = true\n" +
            "history_size = 500\n" +
            "\n" +
            "[server]\n" +
            "record_history = true\n" +
            "history_size = 500\n";

    private HistoryCliTomlConfigIO() {
    }

    /**
     * 读取 TOML 配置；文件不存在时先生成默认配置。
     *
     * @return 根 JsonObject（分节为嵌套对象）；解析失败时返回空对象（并记录告警）
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
            LOG.log(Level.WARNING, "Failed to load TOML config " + file + ", falling back to defaults", e);
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
                current = unquote(line.substring(1, line.length() - 1).trim());
                continue;
            }
            int eq = indexOfAssignment(line);
            if (eq <= 0) {
                continue;
            }
            String key = unquote(line.substring(0, eq).trim());
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
     * 把 {@code root} 写回文件：已有文件按 key 合并（注释/空行/用户自加键保留），
     * 文件不存在时按「标量在前、分节在后」渲染。
     */
    public static void save(Path file, JsonObject root) {
        try {
            String content = Files.exists(file)
                    ? merge(Files.readString(file, StandardCharsets.UTF_8), root)
                    : render(root);
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to save TOML config " + file, e);
        }
    }

    /** 文件不存在（或首次写）时的渲染：标量在前，分节在后。 */
    private static String render(JsonObject root) {
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
        return sb.toString();
    }

    /**
     * 把 root 的值合并进已有文本：替换同（分节，键）行的值、保留行尾注释，
     * 缺失的键补到对应位置，文件中存在但 root 没有的键原样保留。
     */
    private static String merge(String existing, JsonObject root) {
        List<String> lines = new ArrayList<>(Arrays.asList(existing.split("\r?\n", -1)));
        String[] sectionOf = new String[lines.size()];
        Map<String, Integer> headerAt = new LinkedHashMap<>();
        String current = null;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = stripComment(lines.get(i)).trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                current = unquote(trimmed.substring(1, trimmed.length() - 1).trim());
                headerAt.putIfAbsent(current, i);
            }
            sectionOf[i] = current;
        }

        Set<String> handledRoot = new LinkedHashSet<>();
        Map<String, Set<String>> handledSection = new LinkedHashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("\"\"\"")) {
                // 多行字符串：本解析器不支持，原样保留，避免覆盖用户内容。
                continue;
            }
            String stripped = stripComment(line);
            int eq = indexOfAssignment(stripped.trim());
            if (eq <= 0) {
                continue;
            }
            String key = unquote(stripped.trim().substring(0, eq).trim());
            String section = sectionOf[i];
            JsonElement value = lookup(root, section, key);
            if (value == null || value.isJsonObject()) {
                continue;
            }
            lines.set(i, key + " = " + toTomlValue(value) + trailingComment(line));
            if (section == null) {
                handledRoot.add(key);
            } else {
                handledSection.computeIfAbsent(section, k -> new LinkedHashSet<>()).add(key);
            }
        }

        // 补齐缺失的根键（必须插在第一个分节标题之前）。
        List<String> rootMissing = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            if (!e.getValue().isJsonObject() && !handledRoot.contains(e.getKey())) {
                rootMissing.add(e.getKey() + " = " + toTomlValue(e.getValue()));
            }
        }
        if (!rootMissing.isEmpty()) {
            int at = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                if (sectionOf[i] != null) {
                    at = i;
                    break;
                }
            }
            lines.addAll(at, rootMissing);
        }

        // 补齐缺失的分节键：分节已存在则插到该节末尾，否则在文件末尾新建分节。
        StringBuilder tail = new StringBuilder();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            String section = e.getKey();
            Set<String> done = handledSection.getOrDefault(section, Set.of());
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, JsonElement> kv : e.getValue().getAsJsonObject().entrySet()) {
                if (!kv.getValue().isJsonObject() && !done.contains(kv.getKey())) {
                    missing.add(kv.getKey() + " = " + toTomlValue(kv.getValue()));
                }
            }
            if (missing.isEmpty()) {
                continue;
            }
            if (headerAt.containsKey(section)) {
                // 每补一批都重新定位该节末尾，避免使用合并前的旧下标。
                lines.addAll(findSectionEnd(lines, section), missing);
            } else {
                tail.append('\n').append('[').append(section).append("]\n");
                for (String m : missing) {
                    tail.append(m).append('\n');
                }
            }
        }

        String joined = String.join("\n", lines);
        return joined + tail;
    }

    private static int findSectionEnd(List<String> lines, String section) {
        int end = lines.size();
        boolean in = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = stripComment(lines.get(i)).trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String name = unquote(trimmed.substring(1, trimmed.length() - 1).trim());
                if (name.equals(section)) {
                    in = true;
                    continue;
                }
                if (in) {
                    return i;
                }
            } else if (in) {
                end = i + 1;
            }
        }
        return end;
    }

    private static JsonElement lookup(JsonObject root, String section, String key) {
        JsonObject target = (section == null || section.isEmpty()) ? root
                : (root.has(section) && root.getAsJsonObject(section).has(key) ? root.getAsJsonObject(section) : null);
        if (target == null || !target.has(key)) {
            return null;
        }
        return target.get(key);
    }

    /** 行尾注释（含前置空白），无注释返回空串。 */
    private static String trailingComment(String line) {
        int idx = indexOfComment(line);
        return idx >= 0 ? line.substring(idx) : "";
    }

    /** 返回行外（字符串之外）第一个 `#` 的下标；没有则 -1。 */
    private static int indexOfComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\\') {
                i++;          // 跳过转义字符
            } else if (ch == '"') {
                inString = !inString;
            } else if (ch == '#' && !inString) {
                return i;
            }
        }
        return -1;
    }

    private static String stripComment(String line) {
        int idx = indexOfComment(line);
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    /** 找到字符串外的 `=` 位置（支持键名带引号）。 */
    private static int indexOfAssignment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\\') {
                i++;
            } else if (ch == '"') {
                inString = !inString;
            } else if (ch == '=' && !inString) {
                return i;
            }
        }
        return -1;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            try {
                return JsonParser.parseString(s).getAsString();
            } catch (Exception ignored) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private static String toTomlValue(JsonElement value) {
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isBoolean() || value.getAsJsonPrimitive().isNumber()) {
                return value.getAsString();
            }
            return "\"" + escape(value.getAsString()) + "\"";
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(toTomlValue(array.get(i)));
            }
            return sb.append(']').toString();
        }
        return "\"" + escape(value.getAsString()) + "\"";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static JsonElement toPrimitive(String value) {
        String v = value.trim();
        try {
            if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                return JsonParser.parseString(v); // 带引号的字符串，交给 gson 处理转义
            }
            if (v.equals("true") || v.equals("false")) {
                return JsonParser.parseString(v);
            }
            if (v.matches("-?\\d+")) {
                return JsonParser.parseString(v);
            }
            if (v.matches("-?\\d+\\.\\d+")) {
                return JsonParser.parseString(v);
            }
            if (v.startsWith("[") && v.endsWith("]")) {
                return JsonParser.parseString(v); // 数组
            }
            return JsonParser.parseString("\"" + escape(v) + "\"");
        } catch (Exception e) {
            LOG.log(Level.FINE, "Unparsable TOML value, treating as string: " + v);
            return new com.google.gson.JsonPrimitive(v);
        }
    }
}
