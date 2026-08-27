package org.anjisuan608.historycli;

import java.util.List;

/**
 * 平台无关的命令处理器：集中 bash history 的全部子命令逻辑。
 * <p>各平台只需实现三个方法（如何发消息、如何执行展开命令），
 * 修复任一子命令只需修改本类一次，所有端（fabric/neoforge/forge/bukkit/velocity/bungee）同步生效。</p>
 *
 * @param allowFull true=完整命令（支持 reload / -d / ! 系列）；false=普通命令（bash 标准子集）
 */
public abstract class HistoryCommandHandler {

    protected final HistoryStore store;
    private final boolean allowFull;

    protected HistoryCommandHandler(HistoryStore store, boolean allowFull) {
        this.store = store;
        this.allowFull = allowFull;
    }

    /** 平台实现：发送普通消息。 */
    protected abstract void sendMessage(String message);

    /** 平台实现：发送错误消息。 */
    protected abstract void sendError(String message);

    /** 平台实现：真正执行一条命令（供 ! 展开后调用）。 */
    protected abstract void executeCommand(String command);

    /** 统一入口：args 为子命令参数（不含命令名）。完整/普通通过 allowFull 区分。 */
    public void handle(String[] args) {
        if (store == null) {
            sendError("History store not initialized");
            return;
        }
        HistoryParser.Result result = HistoryParser.parse(args);
        if (result == null) {
            return;
        }
        switch (result.action) {
            case EMPTY:
            case LIST:
                list(result.number);
                break;
            case EXECUTE:
                if (!allowFull) {
                    sendError("Unsupported for plain command");
                    break;
                }
                execute(result.command);
                break;
            case CLEAR:
                store.reset();
                sendMessage("History cleared");
                break;
            case WRITE:
                store.write();
                sendMessage("History written to file");
                break;
            case APPEND:
                store.append();
                sendMessage("History appended to file");
                break;
            case READ:
                store.read();
                sendMessage("History loaded from file");
                break;
            case DELETE:
                if (!allowFull) {
                    sendError("Unsupported for plain command");
                    break;
                }
                if (store.delete(result.number)) {
                    sendMessage("Deleted history entry " + result.number);
                } else {
                    sendError("Invalid history index " + result.number);
                }
                break;
            case RELOAD:
                if (!allowFull) {
                    sendError("Unsupported for plain command");
                    break;
                }
                store.read();
                sendMessage("Config reloaded");
                break;
            default:
                sendError("Unknown command");
                break;
        }
    }

    private void execute(String bang) {
        if (bang == null) {
            return;
        }
        String expanded = store.resolve(bang);
        if (expanded == null) {
            sendError("No matching history entry for '" + bang + "'");
            return;
        }
        store.add(expanded);
        executeCommand(stripLeadingSlash(expanded));
    }

    private static String stripLeadingSlash(String command) {
        return command != null && command.startsWith("/") ? command.substring(1) : command;
    }

    private void list(int count) {
        List<String> snapshot = store.snapshot();
        int start = 0;
        int total = snapshot.size();
        if (count > 0 && count < total) {
            start = total - count;
        }
        if (total == 0) {
            sendMessage("No history");
            return;
        }
        for (int i = start; i < total; i++) {
            sendMessage((i + 1) + "  " + snapshot.get(i));
        }
    }
}
