package org.anjisuan608.historycli.fabric.client;

import org.anjisuan608.historycli.HistoryStore;

/**
 * 客户端 ! 展开：解析 /! 系列命令，返回展开结果并写入历史。
 */
public final class ClientHistoryExpander {

    private ClientHistoryExpander() {
    }

    /**
     * 展开 bang 表达式（!! / !n / !-n / !string）。
     *
     * @return 展开后的命令；无匹配返回 null
     */
    public static String expand(String bang) {
        HistoryStore store = HistoryCliFabricClient.store;
        if (store == null) {
            return null;
        }
        String resolved = store.resolve(bang);
        if (resolved != null) {
            store.add(resolved); // 展开后的命令入史
        }
        return resolved;
    }
}
