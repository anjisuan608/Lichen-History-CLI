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

    public static boolean implies(String granted, String required) {
        return granted.equals(ALL) || granted.equals(required);
    }
}
