package org.anjisuan608.historycli;

import java.util.function.Predicate;

/**
 * 权限节点常量。代理端无 op 概念，按子命令级授权，支持 LuckPerms。
 *
 * <p>节点语义：</p>
 * <ul>
 *   <li>{@link #LIST} / {@link #CLEAR} / {@link #WRITE} / {@link #APPEND} / {@link #READ} /
 *       {@link #DELETE} / {@link #EXECUTE} / {@link #RELOAD} —— 子命令级授权；</li>
 *   <li>{@link #ALL}（{@code historycli.*}）—— 通配，等价于拥有全部子命令节点；</li>
 *   <li>{@link #USE}（{@code historycli.use}）—— 基础访问权：只读地查看历史列表与帮助，
 *       不含任何写操作（-c/-w/-a/-r/-d/reload/! 执行）。</li>
 * </ul>
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

    /**
     * 已授予的单个节点是否足以满足所需节点。
     *
     * @param granted 已授予的节点（调用方逐个传入其拥有的节点）
     * @param required 所需节点
     */
    public static boolean implies(String granted, String required) {
        if (granted == null || required == null) {
            return false;
        }
        if (ALL.equals(granted) || granted.equals(required)) {
            return true;
        }
        // historycli.use 只放行只读的列表/帮助。
        return USE.equals(granted) && LIST.equals(required);
    }

    /**
     * 用平台的权限查询回调判定某个动作是否被授权（通配与 {@code use} 一并计入）。
     *
     * <p>平台侧写法：{@code PermissionNode.isGranted(source::hasPermission, action)}。</p>
     *
     * @param hasPermission 平台的单节点权限查询（如 {@code player::hasPermission}）
     * @param action        待判定的解析动作
     */
    public static boolean isGranted(Predicate<String> hasPermission, HistoryParser.Action action) {
        if (hasPermission == null) {
            return false;
        }
        String required = forAction(action);
        return hasPermission.test(ALL) || hasPermission.test(required)
                || (LIST.equals(required) && hasPermission.test(USE));
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
            case LIST_EXPLICIT:
            case HELP:
            case EMPTY:
            default:
                return LIST;
        }
    }
}
