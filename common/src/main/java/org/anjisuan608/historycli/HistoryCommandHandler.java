package org.anjisuan608.historycli;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * 平台无关的命令处理器：集中实现 bash history 的全部子命令逻辑。
 * <p>各平台只需实现四个消息/执行方法（发送可翻译消息、发送错误、发送列表行、执行展开命令），
 * 外加 {@link #reloadConfig()}（重载配置并应用到存储）。修改任一子命令只需修改本类一次，
 * 所有端（fabric/neoforge/forge/bukkit/velocity/bungee）同步生效。</p>
 * <p>消息以翻译键（key）下发给平台层，平台层用各自翻译 API（如 {@code Component.translatable}）渲染。
 * 消息的可变参数是 <b>key 之外的实参</b>，平台层不得把 key 自身作为第一个参数重复传入。</p>
 *
 * @param allowFull true=完整命令（支持 reload / -d / ! 系列）；false=普通命令（bash 标准子集）
 */
public abstract class HistoryCommandHandler {

    /** 本 mod 的翻译键常量。 */
    public static final class Keys {
        public static final String STORE_NOT_INIT = "historycli.msg.store_not_init";
        public static final String HISTORY_CLEARED = "historycli.msg.history_cleared";
        public static final String HISTORY_WRITTEN = "historycli.msg.history_written";
        public static final String HISTORY_APPENDED = "historycli.msg.history_appended";
        public static final String HISTORY_LOADED = "historycli.msg.history_loaded";
        public static final String HISTORY_DELETED = "historycli.msg.history_deleted";
        public static final String INVALID_INDEX = "historycli.msg.invalid_index";
        public static final String CONFIG_RELOADED = "historycli.msg.config_reloaded";
        public static final String CONFIG_RELOAD_FAILED = "historycli.msg.config_reload_failed";
        public static final String IO_ERROR = "historycli.msg.io_error";
        public static final String NOT_EXECUTABLE = "historycli.msg.not_executable";
        public static final String NO_MATCH = "historycli.msg.no_match";
        public static final String UNKNOWN_COMMAND = "historycli.msg.unknown_command";
        public static final String NO_HISTORY = "historycli.msg.no_history";
        public static final String UNSUPPORTED_PLAIN = "historycli.msg.unsupported_plain";
        public static final String NO_PERMISSION = "historycli.msg.no_permission";
        public static final String HELP_TITLE = "historycli.help.title";
        public static final String HELP_USAGE = "historycli.help.usage";
        public static final String HELP_OPTIONS = "historycli.help.options";
        public static final String HELP_OPTIONS_PLAIN = "historycli.help.options_plain";
        public static final String HELP_BANG = "historycli.help.bang";
    }

    private final HistoryStore store;
    private final boolean allowFull;

    protected HistoryCommandHandler(HistoryStore store, boolean allowFull) {
        this.store = store;
        this.allowFull = allowFull;
    }

    /** 是否为完整命令（支持 reload / -d / ! 系列）。 */
    public boolean allowFull() {
        return allowFull;
    }

    /** 获取当前历史存储。子类可覆盖以实现延迟初始化（如集成服务器）。 */
    protected HistoryStore store() {
        return store;
    }

    /** 平台实现：发送可翻译的普通消息（key + 参数）。 */
    protected abstract void sendMessage(String key, Object... args);

    /** 平台实现：发送可翻译的错误消息（key + 参数）。 */
    protected abstract void sendError(String key, Object... args);

    /** 平台实现：发送列表行（字面，不翻译）。 */
    protected abstract void sendRow(String text);

    /** 平台实现：真正执行一条命令（由 ! 展开后调用）。 */
    protected abstract void executeCommand(String command);

    /**
     * 平台实现：重新读取配置文件并应用到历史存储（{@code record_history} / {@code history_size} 等）。
     *
     * @return true=已重载；false=本平台不支持，处理器会回提示「配置重载失败」
     */
    protected boolean reloadConfig() {
        return false;
    }

    /**
     * 统一入口：args 为子命令参数（不含命令名）。完整/普通通过 allowFull 区分。
     *
     * @return 本次操作是否成功。Brigadier 平台据此返回 1/0，
     *         这样命令方块/自动化（{@code /execute}）能感知失败，而不是永远得到"成功"。
     */
    public boolean handle(String[] args) {
        if (store() == null) {
            sendError(Keys.STORE_NOT_INIT);
            return false;
        }
        HistoryParser.Result result = HistoryParser.parse(args);
        if (result == null) {
            return false;
        }
        try {
            return dispatch(result);
        } catch (UncheckedIOException e) {
            // 文件读写失败不应让命令/事件处理器直接抛异常，转成可翻译的错误提示。
            sendError(Keys.IO_ERROR, reason(e));
            return false;
        }
    }

    private boolean dispatch(HistoryParser.Result result) {
        switch (result.action) {
            case EMPTY:
            case LIST:
                list(result.number);
                return true;
            case LIST_EXPLICIT:
                if (!allowFull) {
                    sendError(Keys.UNSUPPORTED_PLAIN);
                    return false;
                }
                list(result.number);
                return true;
            case HELP:
                help();
                return true;
            case EXECUTE:
                if (!allowFull) {
                    sendError(Keys.UNSUPPORTED_PLAIN);
                    return false;
                }
                return execute(result.command);
            case CLEAR:
                store().reset();
                sendMessage(Keys.HISTORY_CLEARED);
                return true;
            case WRITE:
                store().write();
                sendMessage(Keys.HISTORY_WRITTEN);
                return true;
            case APPEND:
                store().append();
                sendMessage(Keys.HISTORY_APPENDED);
                return true;
            case READ:
                store().read();
                sendMessage(Keys.HISTORY_LOADED);
                return true;
            case DELETE:
                if (!allowFull) {
                    sendError(Keys.UNSUPPORTED_PLAIN);
                    return false;
                }
                if (store().delete(result.number)) {
                    sendMessage(Keys.HISTORY_DELETED, result.number);
                    return true;
                }
                sendError(Keys.INVALID_INDEX, result.number);
                return false;
            case RELOAD:
                if (!allowFull) {
                    sendError(Keys.UNSUPPORTED_PLAIN);
                    return false;
                }
                // 重载配置（而不是把历史文件再读一遍——那会造成条目重复）。
                if (reloadConfig()) {
                    sendMessage(Keys.CONFIG_RELOADED);
                    return true;
                }
                sendError(Keys.CONFIG_RELOAD_FAILED);
                return false;
            default:
                sendError(Keys.UNKNOWN_COMMAND);
                return false;
        }
    }

    private boolean execute(String bang) {
        if (bang == null) {
            return false;
        }
        HistoryStore store = store();
        // 平台在进入处理器前已把本次调用记入历史；不剔除的话 !! / !-1 / !n 会解析到自己，
        // 执行后再次进入处理器，形成无限递归（StackOverflowError）。
        store.dropSelfInvocation(bang);
        String expanded = store.resolve(bang);
        if (expanded == null) {
            sendError(Keys.NO_MATCH, bang);
            return false;
        }
        String command = stripLeadingSlash(expanded);
        // 历史文件可被外部编辑，若某行本身就是 `!!` 之类的形式，直接执行会再次进入本方法递归。
        if (command.startsWith("!")) {
            sendError(Keys.NO_MATCH, bang);
            return false;
        }
        store.add(expanded);
        executeCommand(command);
        return true;
    }

    private void help() {
        sendMessage(Keys.HELP_TITLE);
        sendMessage(Keys.HELP_USAGE);
        if (allowFull) {
            sendMessage(Keys.HELP_OPTIONS);
            sendMessage(Keys.HELP_BANG);
        } else {
            sendMessage(Keys.HELP_OPTIONS_PLAIN);
        }
    }

    /** 去掉命令的前导 {@code /}（历史文件存的是带斜杠的原样输入）。 */
    public static String stripLeadingSlash(String command) {
        return command != null && command.startsWith("/") ? command.substring(1) : command;
    }

    private void list(int count) {
        List<String> snapshot = store().snapshot();
        int total = snapshot.size();
        if (total == 0) {
            sendMessage(Keys.NO_HISTORY);
            return;
        }
        int start = (count > 0 && count < total) ? total - count : 0;
        for (int i = start; i < total; i++) {
            sendRow((i + 1) + "  " + snapshot.get(i));
        }
    }

    private static String reason(Exception e) {
        Throwable cause = e.getCause();
        String message = cause != null ? cause.getMessage() : e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
