package org.anjisuan608.historycli;

/**
 * 权限节点常量。代理端无 op 概念，按子命令级授权，支持 LuckPerms。
 */
public final class PermissionNode {

    private PermissionNode() {
    }

    public static final String LIST = "historycli.list";
    public static final String CLEAR = "historycli.clear";
    public static final String WRITE = "historycli.write";
    public static final String APPEND = "historycli.append";
    public static final String READ = "historycli.read";
    public static final String DELETE = "historycli.delete";
    public static final String EXECUTE = "historycli.execute";
    public static final String RELOAD = "historycli.reload";
    public static final String ALL = "historycli.*";
    public static final String USE = "historycli.use";

    public static boolean implies(String granted, String required) {
        return granted.equals(ALL) || granted.equals(required);
    }

    /** 由解析结果的动作映射到对应权限节点。 */
    public static String forAction(HistoryParser.Action action) {
        if (action == null) {
            return LIST;
        }
        switch (action) {
            case CLEAR:
                return CLEAR;
            case WRITE:
                return WRITE;
            case APPEND:
                return APPEND;
            case READ:
                return READ;
            case DELETE:
                return DELETE;
            case EXECUTE:
                return EXECUTE;
            case RELOAD:
                return RELOAD;
            case LIST:
            case HELP:
            case EMPTY:
            default:
                return LIST;
        }
    }
}
