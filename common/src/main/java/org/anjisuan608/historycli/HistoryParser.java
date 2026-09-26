package org.anjisuan608.historycli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 命令参数解析：history / historycli 子命令与 bash 历史展开（!n、!!、!-n、!string）。
 * 解析结果为平台无关的动作指令，由各平台适配层执行。
 */
public final class HistoryParser {

    /** 解析结果动作类型。 */
    public enum Action {
        LIST,          // 列表（默认动作 / <num>）
        LIST_EXPLICIT, // 显式 list 子命令（仅完整命令可用，同 !!）
        CLEAR,         // -c
        WRITE,         // -w
        APPEND,        // -a
        READ,          // -r
        DELETE,        // -d <编号>
        RELOAD,        // reload
        EXECUTE,       // ! 展开执行
        HELP,          // help / ?（用户显式求助）
        PARSE_ERROR,   // 参数无法解析（显示帮助，但要按"失败"回报给调用方/自动化）
        EMPTY          // 无参数
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

    private HistoryParser() {
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
            return Result.execute(first);
        }

        switch (first.toLowerCase(Locale.ROOT)) {
            case "reload":
                return Result.of(Action.RELOAD);
            case "help":
            case "?":
                return Result.of(Action.HELP);
            case "list":
                return Result.of(Action.LIST_EXPLICIT);
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
                    return Result.of(Action.PARSE_ERROR);
                }
                try {
                    return Result.of(Action.DELETE, Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    return Result.of(Action.PARSE_ERROR);
                }
            default:
                try {
                    return Result.of(Action.LIST, Integer.parseInt(first));
                } catch (NumberFormatException e) {
                    // 无法解析的参数：给用户看帮助，但对调用方（命令方块/自动化）算失败
                    return Result.of(Action.PARSE_ERROR);
                }
        }
    }

    /** 按空白拆分命令参数字符串。 */
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
