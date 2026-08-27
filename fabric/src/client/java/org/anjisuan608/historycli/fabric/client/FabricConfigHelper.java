package org.anjisuan608.historycli.fabric.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.anjisuan608.historycli.HistoryStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取 config/lichen-history-cli.json 并应用到 HistoryStore。
 * 缺省配置使用 common 默认值；文件不存在时使用默认。
 */
public final class FabricConfigHelper {

    private FabricConfigHelper() {
    }

    public static void apply(HistoryStore store) {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir();
            Path file = dir.resolve("lichen-history-cli.json");
            if (Files.exists(file)) {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject client = root.has("client") ? root.getAsJsonObject("client") : new JsonObject();
                if (client.has("history_size")) {
                    store.setMaxSize(client.get("history_size").getAsInt());
                }
                if (client.has("record_history")) {
                    store.setRecordEnabled(client.get("record_history").getAsBoolean());
                }
            }
        } catch (Exception e) {
            // 配置异常时忽略，使用默认值
        }
    }
}
