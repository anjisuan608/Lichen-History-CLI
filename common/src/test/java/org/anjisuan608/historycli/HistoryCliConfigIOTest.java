package org.anjisuan608.historycli;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryCliConfigIOTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("lichen-history-cli.json");
    }

    @Test
    void createsDefaultConfigOnFirstLoad() throws IOException {
        JsonObject root = HistoryCliConfigIO.loadOrCreate(file());

        assertTrue(Files.exists(file()), "首次加载应生成默认配置");
        String written = Files.readString(file(), StandardCharsets.UTF_8);
        assertTrue(written.contains("history_size"), "默认配置应包含历史上限");
        assertFalse(HistoryCliConfigIO.getBool(root, null, "enable_integrated_history", true));
        assertTrue(HistoryCliConfigIO.getBool(root, "server", "record_history", false));
        assertTrue(HistoryCliConfigIO.getBool(root, "client", "record_history", false));
        assertEquals(500, HistoryCliConfigIO.getInt(root, "server", "history_size", 0));
        assertEquals(500, HistoryCliConfigIO.getInt(root, "client", "history_size", 0));
    }

    @Test
    void brokenConfigFallsBackToDefaultsWithoutThrowing() throws IOException {
        Files.writeString(file(), "{ not valid json", StandardCharsets.UTF_8);

        JsonObject root = HistoryCliConfigIO.loadOrCreate(file());

        assertTrue(root.isJsonObject());
        // 解析失败返回空对象 → 全部回落到代码里的默认值
        assertEquals(500, HistoryCliConfigIO.getInt(root, "client", "history_size", 500));
        assertTrue(HistoryCliConfigIO.getBool(root, "client", "record_history", true));
        assertTrue(HistoryCliConfigIO.getBool(root, "server", "record_history", true));
        assertFalse(HistoryCliConfigIO.getBool(root, null, "enable_integrated_history", false));
    }

    @Test
    void accessorsReturnCallerDefaultsOnMissingOrBadValues() {
        JsonObject root = HistoryCliConfigIO.loadOrCreate(dir.resolve("absent.json"));

        assertEquals(123, HistoryCliConfigIO.getInt(root, "client", "nope", 123));
        assertEquals(123, HistoryCliConfigIO.getInt(root, "missing_section", "history_size", 123));
        assertTrue(HistoryCliConfigIO.getBool(root, "missing_section", "record_history", true));

        JsonObject typed = new JsonObject();
        typed.addProperty("history_size", "not a number");
        typed.addProperty("record_history", "maybe");
        assertEquals(7, HistoryCliConfigIO.getInt(typed, null, "history_size", 7));
        // Gson 会把非布尔字符串 parse 成 false，总之不会抛异常
        assertFalse(HistoryCliConfigIO.getBool(typed, null, "record_history", true));
    }
}
