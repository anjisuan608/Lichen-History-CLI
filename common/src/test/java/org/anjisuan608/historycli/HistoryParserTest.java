package org.anjisuan608.historycli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void malformedInputsFallBackToHelp() {
        assertEquals(HistoryParser.Action.HELP, action("-d"));
        assertEquals(HistoryParser.Action.HELP, action("-d", "abc"));
        assertEquals(HistoryParser.Action.HELP, action("not-a-number"));
        // 超出 int 范围也必须走 help，而不是抛 NumberFormatException
        assertEquals(HistoryParser.Action.HELP, action("99999999999999999999"));
        assertEquals(HistoryParser.Action.HELP, action("-d", "99999999999999999999"));
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
}
