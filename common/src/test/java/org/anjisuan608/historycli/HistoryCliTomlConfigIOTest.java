package org.anjisuan608.historycli;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手写 TOML 解析/写回的回归测试。
 * <p>重点是 {@code save} 必须「按 key 合并」而不是整文件重写——配置界面每保存一次就
 * 抹掉用户注释是不可接受的。</p>
 */
class HistoryCliTomlConfigIOTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("lichen-history-cli.toml");
    }

    @Test
    void parseReadsRootSectionsAndComments() {
        String toml = ""
                + "# 顶部注释\n"
                + "enable_integrated_history = true  # 行尾注释\n"
                + "\n"
                + "[client]\n"
                + "record_history = false\n"
                + "history_size = 42\n"
                + "note = \"has # inside\"\n"
                + "\n"
                + "[server]\n"
                + "history_size = 7\n";

        JsonObject root = HistoryCliTomlConfigIO.parse(toml);

        assertTrue(HistoryCliConfigIO.getBool(root, null, "enable_integrated_history", false));
        assertFalse(HistoryCliConfigIO.getBool(root, "client", "record_history", true));
        assertEquals(42, HistoryCliConfigIO.getInt(root, "client", "history_size", 0));
        assertEquals(7, HistoryCliConfigIO.getInt(root, "server", "history_size", 0));
        assertEquals("has # inside", root.getAsJsonObject("client").get("note").getAsString());
    }

    @Test
    void parseSupportsQuotedSectionNamesAndArrays() {
        String toml = ""
                + "[\"client\"]\n"
                + "aliases = [\"a\", \"b\"]\n"
                + "history_size = 10\n";

        JsonObject root = HistoryCliTomlConfigIO.parse(toml);
        assertEquals(10, HistoryCliConfigIO.getInt(root, "client", "history_size", 0));
        assertEquals(2, root.getAsJsonObject("client").getAsJsonArray("aliases").size());
    }

    @Test
    void savePreservesCommentsAndUnknownKeys() throws IOException {
        Files.write(file(), List.of(
                "# 我自己的注释，不能被抹掉",
                "enable_integrated_history = false",
                "custom_key = 123",
                "",
                "[client]",
                "record_history = true",
                "history_size = 500",
                "my_note = \"keep me\"",
                "",
                "[server]",
                "record_history = true",
                "history_size = 500"
        ), StandardCharsets.UTF_8);

        JsonObject root = HistoryCliTomlConfigIO.loadOrCreate(file());
        root.addProperty("enable_integrated_history", true);
        root.getAsJsonObject("client").addProperty("history_size", 99);
        HistoryCliTomlConfigIO.save(file(), root);

        List<String> lines = Files.readAllLines(file(), StandardCharsets.UTF_8);
        String text = String.join("\n", lines);

        assertTrue(text.contains("# 我自己的注释，不能被抹掉"), "注释必须保留");
        assertTrue(text.contains("custom_key = 123"), "用户自加的键必须保留");
        assertTrue(text.contains("my_note = \"keep me\""), "分节内用户自加的键必须保留");
        assertTrue(text.contains("enable_integrated_history = true"), "被管理的键要更新");
        assertTrue(text.contains("history_size = 99"), "分节内的键要更新");
        assertTrue(text.contains("history_size = 500"), "未改动的键保持原值");

        // 再读一次，值应当正确
        JsonObject reread = HistoryCliTomlConfigIO.loadOrCreate(file());
        assertTrue(HistoryCliConfigIO.getBool(reread, null, "enable_integrated_history", false));
        assertEquals(99, HistoryCliConfigIO.getInt(reread, "client", "history_size", 0));
        assertEquals(500, HistoryCliConfigIO.getInt(reread, "server", "history_size", 0));
    }

    @Test
    void saveAppendsMissingKeysToTheirSection() throws IOException {
        Files.write(file(), List.of("[client]", "record_history = true"), StandardCharsets.UTF_8);

        JsonObject root = HistoryCliTomlConfigIO.loadOrCreate(file());
        root.addProperty("enable_integrated_history", true);
        root.getAsJsonObject("client").addProperty("history_size", 500);
        if (!root.has("server")) {
            JsonObject server = new JsonObject();
            server.addProperty("record_history", true);
            server.addProperty("history_size", 500);
            root.add("server", server);
        }
        HistoryCliTomlConfigIO.save(file(), root);

        List<String> lines = Files.readAllLines(file(), StandardCharsets.UTF_8);
        // 根键必须落在第一个分节标题之前，否则 TOML 解析时会归到分节里
        int firstSection = -1;
        int rootKey = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (firstSection < 0 && line.startsWith("[")) {
                firstSection = i;
            }
            if (line.startsWith("enable_integrated_history")) {
                rootKey = i;
            }
        }
        assertTrue(firstSection >= 0, "应当存在分节");
        assertTrue(rootKey >= 0, "根键应当被补上");
        assertTrue(rootKey < firstSection, "根键必须位于分节标题之前");

        JsonObject reread = HistoryCliTomlConfigIO.loadOrCreate(file());
        assertTrue(HistoryCliConfigIO.getBool(reread, null, "enable_integrated_history", false));
        assertEquals(500, HistoryCliConfigIO.getInt(reread, "client", "history_size", 0));
        assertEquals(500, HistoryCliConfigIO.getInt(reread, "server", "history_size", 0));
    }

    @Test
    void defaultConfigRoundTrips() {
        JsonObject root = HistoryCliTomlConfigIO.parse(HistoryCliTomlConfigIO.DEFAULT_CONFIG);
        assertEquals(500, HistoryCliConfigIO.getInt(root, "client", "history_size", 0));
        assertEquals(500, HistoryCliConfigIO.getInt(root, "server", "history_size", 0));
        assertFalse(HistoryCliConfigIO.getBool(root, null, "enable_integrated_history", true));
    }

    @Test
    void loadOrCreateCreatesDefaultFileWhenMissing() throws IOException {
        JsonObject root = HistoryCliTomlConfigIO.loadOrCreate(file());
        assertTrue(Files.exists(file()));
        assertEquals(500, HistoryCliConfigIO.getInt(root, "client", "history_size", 0));
    }
}
