package org.anjisuan608.historycli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 命令参数解析：history / historycli 子命令与 bash 历史展开（!n、!!、!-n、!string）。
 * 解析结果为平台无关的动作指令，由各平台适配层执行。
 */
public final class HistoryParser {

    /** 解析结果动作类型。 */
    public enum Action {
        LIST,       // 列表
        CLEAR,      // -c
        WRITE,      // -w
        APPEND,     // -a
        READ,       // -r
        DELETE,     // -d <编号>
        RELOAD,     // reload
        EXECUTE,    // ! 展开执行
        HELP,       // 帮助/错误
        EMPTY       // 无参数
    }

    public static final class Result {
        public final Action action;
        public final int number;
        public final String command;

        private Result(Action action, int number, String command) {
            this.action = action;
            this.number = number;
            this.command = command;
        }

        static Result of(Action action) {
            return new Result(action, 0, null);
        }

        static Result of(Action action, int number) {
            return new Result(action, number, null);
        }

        static Result execute(String command) {
            return new Result(Action.EXECUTE, 0, command);
        }
    }

    private static final Pattern BANG_NUMBER = Pattern.compile("^!(\\d+)$");
    private static final Pattern BANG_NEGATIVE = Pattern.compile("^-!(\\d+)$");
    private static final Pattern BANG_PREFIX = Pattern.compile("^!(.+)$");

    private HistoryParser() {
    }

    /**
     * 解析历史展开表达式（!! / !n / !-n / !string）。
     *
     * @return 展开表达式；非 `!` 开头返回 null
     */
    public static String parseBangExpression(String input) {
        if (input == null || !input.startsWith("!")) {
            return null;
        }
        return input;
    }

    /**
     * 解析命令子参数（不含命令名本身）。
     */
    public static Result parse(String... args) {
        if (args == null || args.length == 0) {
            return Result.of(Action.EMPTY);
        }
        String first = args[0];

        // ! 系列展开
        if (first.startsWith("!")) {
            String expr = first;
            // historycli !-n
            if (expr.equals("!!")) {
                return Result.execute(null); // 上一条由调用方展开
            }
            Matcher neg = BANG_NEGATIVE.matcher(expr);
            if (neg.matches()) {
                return Result.execute(null);
            }
            Matcher num = BANG_NUMBER.matcher(expr);
            if (num.matches()) {
                return Result.execute(null);
            }
            if (expr.length() > 1) {
                return Result.execute(null);
            }
        }

        switch (first.toLowerCase(Locale.ROOT)) {
            case "reload":
                return Result.of(Action.RELOAD);
            case "-c":
                return Result.of(Action.CLEAR);
            case "-w":
                return Result.of(Action.WRITE);
            case "-a":
                return Result.of(Action.APPEND);
            case "-r":
                return Result.of(Action.READ);
            case "-d":
                if (args.length < 2) {
                    return Result.of(Action.HELP);
                }
                try {
                    return Result.of(Action.DELETE, Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    return Result.of(Action.HELP);
                }
            default:
                try {
                    return Result.of(Action.LIST, Integer.parseInt(first));
                } catch (NumberFormatException e) {
                    return Result.of(Action.HELP);
                }
        }
    }

    /** 便捷方法：返回最近执行的命令（由适配层传入历史缓冲决定）。 */
    public static List<String> split(String input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        for (String s : input.trim().split("\\s+")) {
            if (!s.isEmpty()) {
                parts.add(s);
            }
        }
        return parts;
    }
}
