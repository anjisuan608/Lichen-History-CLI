package org.anjisuan608.historycli;

import java.util.List;

/**
 * 骞冲彴鏃犲叧鐨勫懡浠ゅ鐞嗗櫒锛氶泦涓?bash history 鐨勫叏閮ㄥ瓙鍛戒护閫昏緫銆? * <p>鍚勫钩鍙板彧闇€瀹炵幇鍥涗釜鏂规硶锛堝彂閫佸彲缈昏瘧娑堟伅銆佸彂閫侀敊璇€佸彂閫佸垪琛ㄨ銆佹墽琛屽睍寮€鍛戒护锛夛紝
 * 淇浠讳竴瀛愬懡浠ゅ彧闇€淇敼鏈被涓€娆★紝鎵€鏈夌锛坒abric/neoforge/forge/bukkit/velocity/bungee锛夊悓姝ョ敓鏁堛€? * 娑堟伅浠ョ炕璇戦敭锛坘ey锛変笅鍙戠粰骞冲彴灞傦紝骞冲彴灞傜敤鍚勮嚜缈昏瘧 API锛堝 {@code Component.translatable}锛夋覆鏌撱€?/p>
 *
 * @param allowFull true=瀹屾暣鍛戒护锛堟敮鎸?reload / -d / ! 绯诲垪锛夛紱false=鏅€氬懡浠わ紙bash 鏍囧噯瀛愰泦锛? */
public abstract class HistoryCommandHandler {

    /** 鏈?mod 鐨勭炕璇戦敭甯搁噺銆?*/
    public static final class Keys {
        public static final String STORE_NOT_INIT = "historycli.msg.store_not_init";
        public static final String HISTORY_CLEARED = "historycli.msg.history_cleared";
        public static final String HISTORY_WRITTEN = "historycli.msg.history_written";
        public static final String HISTORY_APPENDED = "historycli.msg.history_appended";
        public static final String HISTORY_LOADED = "historycli.msg.history_loaded";
        public static final String HISTORY_DELETED = "historycli.msg.history_deleted";
        public static final String INVALID_INDEX = "historycli.msg.invalid_index";
        public static final String CONFIG_RELOADED = "historycli.msg.config_reloaded";
        public static final String NO_MATCH = "historycli.msg.no_match";
        public static final String UNKNOWN_COMMAND = "historycli.msg.unknown_command";
        public static final String NO_HISTORY = "historycli.msg.no_history";
        public static final String UNSUPPORTED_PLAIN = "historycli.msg.unsupported_plain";
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

    /** 鑾峰彇褰撳墠鍘嗗彶瀛樺偍銆傚瓙绫诲彲瑕嗙洊浠ュ疄鐜板欢杩熷垵濮嬪寲锛堝闆嗘垚鏈嶅姟鍣級銆?*/
    protected HistoryStore store() {
        return store;
    }

    /** 骞冲彴瀹炵幇锛氬彂閫佸彲缈昏瘧鐨勬櫘閫氭秷鎭紙key + 鍙傛暟锛夈€?*/
    protected abstract void sendMessage(String key, Object... args);

    /** 骞冲彴瀹炵幇锛氬彂閫佸彲缈昏瘧鐨勯敊璇秷鎭紙key + 鍙傛暟锛夈€?*/
    protected abstract void sendError(String key, Object... args);

    /** 骞冲彴瀹炵幇锛氬彂閫佸垪琛ㄨ锛堝瓧闈紝涓嶇炕璇戯級銆?*/
    protected abstract void sendRow(String text);

    /** 骞冲彴瀹炵幇锛氱湡姝ｆ墽琛屼竴鏉″懡浠わ紙渚?! 灞曞紑鍚庤皟鐢級銆?*/
    protected abstract void executeCommand(String command);

    /** 缁熶竴鍏ュ彛锛歛rgs 涓哄瓙鍛戒护鍙傛暟锛堜笉鍚懡浠ゅ悕锛夈€傚畬鏁?鏅€氶€氳繃 allowFull 鍖哄垎銆?*/
    public void handle(String[] args) {
        if (store() == null) {
            sendError(Keys.STORE_NOT_INIT);
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
            case LIST_EXPLICIT:
                if (!allowFull) {
                    sendError(Keys.UNSUPPORTED_PLAIN);
                    break;
                }
                list(result.number);
                break;
            case HELP:
                help();
                break;
            case EXECUTE:
                if (!allowFull) {
                    sendError(Keys.UNSUPPORTED_PLAIN);
                    break;
                }
                execute(result.command);
                break;
            case CLEAR:
                store().reset();
                sendMessage(Keys.HISTORY_CLEARED);
                break;
            case WRITE:
                store().write();
                sendMessage(Keys.HISTORY_WRITTEN);
                break;
            case APPEND:
                store().append();
                sendMessage(Keys.HISTORY_APPENDED);
                break;
            case READ:
                store().read();
                sendMessage(Keys.HISTORY_LOADED);
                break;
            case DELETE:
                if (!allowFull) {
                    sendError(Keys.UNSUPPORTED_PLAIN);
                    break;
                }
                if (store().delete(result.number)) {
                    sendMessage(Keys.HISTORY_DELETED, result.number);
                } else {
                    sendError(Keys.INVALID_INDEX, result.number);
                }
                break;
            case RELOAD:
                if (!allowFull) {
                    sendError(Keys.UNSUPPORTED_PLAIN);
                    break;
                }
                store().read();
                sendMessage(Keys.CONFIG_RELOADED);
                break;
            default:
                sendError(Keys.UNKNOWN_COMMAND);
                break;
        }
    }

    private void execute(String bang) {
        if (bang == null) {
            return;
        }
        String expanded = store().resolve(bang);
        if (expanded == null) {
            sendError(Keys.NO_MATCH, bang);
            return;
        }
        store().add(expanded);
        executeCommand(stripLeadingSlash(expanded));
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
        int start = 0;
        int total = snapshot.size();
        if (count > 0 && count < total) {
            start = total - count;
        }
        if (total == 0) {
            sendMessage(Keys.NO_HISTORY);
            return;
        }
        for (int i = start; i < total; i++) {
            sendRow((i + 1) + "  " + snapshot.get(i));
        }
    }
}
