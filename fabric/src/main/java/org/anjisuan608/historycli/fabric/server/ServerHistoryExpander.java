package org.anjisuan608.historycli.fabric.server;

import org.anjisuan608.historycli.HistoryStore;
import org.anjisuan608.historycli.fabric.HistoryCliFabric;

/**
 * 服务端 ! 展开：解析 /historycliserver !1 或控制台 ! 系列，返回展开结果并写入服务端历史。
 */
public final class ServerHistoryExpander {

    private ServerHistoryExpander() {
    }

    public static String expand(String bang) {
        HistoryStore store = HistoryCliFabric.serverStore;
        if (store == null) {
            return null;
        }
        String resolved = store.resolve(bang);
        if (resolved == null || resolved.startsWith("!")) {
            // 无匹配；或匹配到历史里的字面量 `!!` 行——执行它会再次进入展开逻辑造成死循环，按无匹配处理。
            return null;
        }
        store.add(resolved); // 展开后的命令入史
        return resolved;
    }
}
