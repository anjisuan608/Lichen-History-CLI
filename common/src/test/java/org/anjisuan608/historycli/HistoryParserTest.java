package org.anjisuan608.historycli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HistoryParserTest {

    private static HistoryParser.Action action(String... args) {
        return HistoryParser.parse(args).action;
    }

    @Test
    void emptyAndList() {
        assertEquals(HistoryParser.Action.EMPTY, action());
        assertEquals(HistoryParser.Action.LIST, action("10"));
        assertEquals(HistoryParser.Action.LIST_EXPLICIT, action("list"));
    }

    @Test
    void options() {
        assertEquals(HistoryParser.Action.CLEAR, action("-c"));
        assertEquals(HistoryParser.Action.WRITE, action("-w"));
        assertEquals(HistoryParser.Action.APPEND, action("-a"));
        assertEquals(HistoryParser.Action.READ, action("-r"));
        assertEquals(HistoryParser.Action.DELETE, action("-d", "3"));
        assertEquals(HistoryParser.Action.RELOAD, action("reload"));
        assertEquals(HistoryParser.Action.HELP, action("help"));
        assertEquals(HistoryParser.Action.HELP, action("?"));
    }

    @Test
    void bangGoesToExecute() {
        assertEquals(HistoryParser.Action.EXECUTE, action("!!"));
        assertEquals(HistoryParser.Action.EXECUTE, action("!5"));
        assertEquals(HistoryParser.Action.EXECUTE, action("!-2"));
        assertEquals(HistoryParser.Action.EXECUTE, action("!say"));
    }

    @Test
    void malformedInputsAreParseErrorsNotHelp() {
        // 显式求助才是 HELP（返回成功）；参数看不懂要判失败，让命令方块/自动化能感知
        assertEquals(HistoryParser.Action.HELP, action("help"));
        assertEquals(HistoryParser.Action.HELP, action("?"));
        assertEquals(HistoryParser.Action.PARSE_ERROR, action("-d"));
        assertEquals(HistoryParser.Action.PARSE_ERROR, action("-d", "abc"));
        assertEquals(HistoryParser.Action.PARSE_ERROR, action("not-a-number"));
        // 超出 int 范围也必须是 parse error，而不是抛 NumberFormatException
        assertEquals(HistoryParser.Action.PARSE_ERROR, action("99999999999999999999"));
        assertEquals(HistoryParser.Action.PARSE_ERROR, action("-d", "99999999999999999999"));
    }

    @Test
    void executeCarriesTheExpression() {
        HistoryParser.Result result = HistoryParser.parse(new String[]{"!!"});
        assertEquals(HistoryParser.Action.EXECUTE, result.action);
        assertEquals("!!", result.command);
    }

    @Test
    void splitSplitsOnWhitespace() {
        assertEquals(List.of(), HistoryParser.split(""));
        assertEquals(List.of(), HistoryParser.split(null));
        assertEquals(List.of("!!"), HistoryParser.split("  !! "));
        assertEquals(List.of("-d", "3"), HistoryParser.split("-d   3"));
    }

    @Test
    void stripLeadingSlashRemovesExactlyOneSlash() {
        assertEquals("say hi", HistoryCommandHandler.stripLeadingSlash("/say hi"));
        assertEquals("say hi", HistoryCommandHandler.stripLeadingSlash("say hi"));
        assertEquals("/say hi", HistoryCommandHandler.stripLeadingSlash("//say hi"));
        assertNull(HistoryCommandHandler.stripLeadingSlash(null));
    }

    @Test
    void everyCandidateOptionIsParseable() {
        // 候选选项的单一事实源（FULL_OPTIONS/PLAIN_OPTIONS）里的每一项都必须真能被 parse 识别，
        // 否则补全会推荐出被判成 PARSE_ERROR 的无意义选项（五端共用这一份常量）
        for (String option : HistoryParser.FULL_OPTIONS) {
            if ("-d".equals(option)) {
                // -d 必须跟编号，单独出现判 PARSE_ERROR 是**正确**行为，单独断言它
                assertEquals(HistoryParser.Action.PARSE_ERROR, HistoryParser.parse(new String[]{"-d"}).action,
                        "-d 缺编号时应判 PARSE_ERROR");
                continue;
            }
            HistoryParser.Result r = HistoryParser.parse(new String[]{option});
            assertNotNull(r, "FULL_OPTIONS 中的 '" + option + "' 未被 parse 识别");
            assertFalse(r.action == HistoryParser.Action.PARSE_ERROR,
                    "FULL_OPTIONS 中的 '" + option + "' 被判成 PARSE_ERROR");
        }
        for (String option : HistoryParser.PLAIN_OPTIONS) {
            assertFalse(HistoryParser.parse(new String[]{option}).action == HistoryParser.Action.PARSE_ERROR,
                    "PLAIN_OPTIONS 中的 '" + option + "' 被判成 PARSE_ERROR");
        }
    }
}
