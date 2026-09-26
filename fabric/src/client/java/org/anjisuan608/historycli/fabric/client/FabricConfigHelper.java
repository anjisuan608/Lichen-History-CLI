package org.anjisuan608.historycli.fabric.client;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.anjisuan608.historycli.HistoryCliConfigIO;
import org.anjisuan608.historycli.HistoryStore;

import java.nio.file.Path;

/**
 * 读取 config/lichen-history-cli.json 并应用到 HistoryStore。
 * 首次启动生成默认配置；文件不存在时使用默认值。
 */
public final class FabricConfigHelper {

    private FabricConfigHelper() {
    }

    /** 读取配置并应用到给定存储，返回是否成功应用。 */
    public static boolean apply(HistoryStore store) {
        if (store == null) {
            return false;
        }
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("lichen-history-cli.json");
            JsonObject root = HistoryCliConfigIO.loadOrCreate(file);
            store.setMaxSize(HistoryCliConfigIO.getInt(root, "client", "history_size", 500));
            store.setRecordEnabled(HistoryCliConfigIO.getBool(root, "client", "record_history", true));
            return true;
        } catch (Exception e) {
            // 配置异常时保留当前值（HistoryCliConfigIO 内部已记录告警）
            return false;
        }
    }
}
