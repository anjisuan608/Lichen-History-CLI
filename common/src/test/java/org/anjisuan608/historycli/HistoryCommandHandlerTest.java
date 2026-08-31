package org.anjisuan608.historycli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryCommandHandlerTest {

    private static final class FakeHandler extends HistoryCommandHandler {

        final List<String> messages = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> executed = new ArrayList<>();

        FakeHandler(HistoryStore store, boolean allowFull) {
            super(store, allowFull);
        }

        @Override
        protected void sendMessage(String key, Object... args) {
            messages.add(args.length == 0 ? key : key + " " + String.join(" ", toStr(args)));
        }

        @Override
        protected void sendError(String key, Object... args) {
            errors.add(args.length == 0 ? key : key + " " + String.join(" ", toStr(args)));
        }

        @Override
        protected void sendRow(String text) {
            messages.add(text);
        }

        @Override
        protected void executeCommand(String command) {
            executed.add(command);
        }

        private static String[] toStr(Object[] args) {
            String[] out = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                out[i] = String.valueOf(args[i]);
            }
            return out;
        }
    }

    private HistoryStore store() {
        HistoryStore store = new HistoryStore(Path.of("unused"));
        store.setMaxSize(0);
        store.add("/tp @s 0 100 0");
        store.add("say hello");
        store.add("summon zombie");
        return store;
    }

    @Test
    void fullList() {
        FakeHandler h = new FakeHandler(store(), true);
        h.handle(new String[0]);
        assertTrue(h.messages.stream().anyMatch(m -> m.equals("3  summon zombie")));
    }

    @Test
    void fullExplicitList() {
        FakeHandler h = new FakeHandler(store(), true);
        h.handle(new String[]{"list"});
        assertTrue(h.messages.stream().anyMatch(m -> m.equals("3  summon zombie")));
    }

    @Test
    void plainExplicitListRejected() {
        FakeHandler h = new FakeHandler(store(), false);
        h.handle(new String[]{"list"});
        assertTrue(h.errors.contains("historycli.msg.unsupported_plain"));
    }

    @Test
    void fullExecuteStripsLeadingSlash() {
        FakeHandler h = new FakeHandler(store(), true);
        h.handle(new String[]{"!1"});
        assertEquals(1, h.executed.size());
        assertEquals("tp @s 0 100 0", h.executed.get(0));
    }

    @Test
    void plainRejectsExecute() {
        FakeHandler h = new FakeHandler(store(), false);
        h.handle(new String[]{"!1"});
        assertTrue(h.executed.isEmpty());
        assertTrue(!h.errors.isEmpty());
    }

    @Test
    void plainRejectsReloadAndDelete() {
        FakeHandler h = new FakeHandler(store(), false);
        h.handle(new String[]{"reload"});
        h.handle(new String[]{"-d", "1"});
        assertTrue(!h.errors.isEmpty());
    }

    @Test
    void fullClearAndDelete() {
        FakeHandler h = new FakeHandler(store(), true);
        h.handle(new String[]{"-d", "2"});
        assertEquals(1, h.messages.size());
        assertTrue(h.messages.contains("historycli.msg.history_deleted 2"));
        h.handle(new String[]{"-c"});
        assertTrue(h.messages.contains("historycli.msg.history_cleared"));
    }
}
