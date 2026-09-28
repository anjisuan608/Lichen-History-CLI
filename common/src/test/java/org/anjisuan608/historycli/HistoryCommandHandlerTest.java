package org.anjisuan608.historycli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryCommandHandlerTest {

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tempDir;

    private static final class FakeHandler extends HistoryCommandHandler {

        final List<String> messages = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> executed = new ArrayList<>();
        boolean reloadResult = true;

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

        @Override
        protected boolean reloadConfig() {
            return reloadResult;
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

    // ---------- 回归：以下用例在修复前会失败 ----------

    @Test
    void reloadUsesConfigHookAndDoesNotRereadHistoryFile() throws java.io.IOException {
        java.nio.file.Path file = tempDir.resolve("history.log");
        java.nio.file.Files.write(file, java.util.List.of("one", "two"),
                java.nio.charset.StandardCharsets.UTF_8);

        HistoryStore store = new HistoryStore(file);
        store.setMaxSize(0);
        store.read();
        assertEquals(2, store.size());

        FakeHandler h = new FakeHandler(store, true);
        h.handle(new String[]{"reload"});

        assertTrue(h.messages.contains("historycli.msg.config_reloaded"),
                "reload 应回「配置已重载」，而不是把历史文件再读一遍");
        assertEquals(2, store.size(), "reload 不应导致历史条目重复");

        h.reloadResult = false;
        h.handle(new String[]{"reload"});
        assertTrue(h.errors.contains("historycli.msg.config_reload_failed"));
    }

    @Test
    void fileErrorsSurfaceAsTranslatedKeyInsteadOfThrowing() throws java.io.IOException {
        // 用目录占住历史文件路径，制造写失败
        java.nio.file.Path blocked = tempDir.resolve("blocked");
        java.nio.file.Files.createDirectory(blocked);
        HistoryStore store = new HistoryStore(blocked);
        store.setMaxSize(0);
        store.add("a");

        FakeHandler h = new FakeHandler(store, true);
        h.handle(new String[]{"-w"});   // 修复前这里会抛 UncheckedIOException

        assertTrue(h.errors.stream().anyMatch(e -> e.startsWith("historycli.msg.io_error")),
                "IO 故障必须转成可翻译的错误消息：" + h.errors);
    }

    @Test
    void selfRecordedBangDoesNotResolveToItself() {
        HistoryStore store = store();
        // 平台在把命令交给处理器之前已经把本次调用记入历史
        store.add("historyserver !!");

        FakeHandler h = new FakeHandler(store, true);
        h.handle(new String[]{"!!"});

        assertEquals(1, h.executed.size());
        assertEquals("summon zombie", h.executed.get(0),
                "!! 必须展开成上一条真实命令，而不是这次 bang 调用本身");
        assertTrue(h.errors.isEmpty());
    }

    @Test
    void bangExpandingToABangExpressionIsRejected() {
        HistoryStore store = new HistoryStore(tempDir.resolve("unused"));
        store.setMaxSize(0);
        store.add("!!"); // 历史文件里被外部编辑成了一条字面量

        FakeHandler h = new FakeHandler(store, true);
        h.handle(new String[]{"!1"});   // 指向那条字面量

        assertTrue(h.executed.isEmpty(), "展开结果仍是 ! 形式时必须拒绝，否则会无限递归");
        assertTrue(h.errors.contains("historycli.msg.no_match !1"));
    }

    @Test
    void missingStoreReportsKey() {
        FakeHandler h = new FakeHandler(null, true);
        h.handle(new String[0]);
        assertTrue(h.errors.contains("historycli.msg.store_not_init"));
    }

    @Test
    void handleReportsSuccessAndFailureForAutomation() {
        HistoryStore store = store();

        FakeHandler full = new FakeHandler(store, true);
        assertTrue(full.handle(new String[0]), "列出历史应视为成功");
        assertTrue(full.handle(new String[]{"help"}), "帮助应视为成功");
        assertTrue(full.handle(new String[]{"-d", "2"}), "删除存在的条目应成功");
        assertFalse(full.handle(new String[]{"-d", "99"}), "删除越界条目应失败");
        assertFalse(full.handle(new String[]{"!999"}), "展开无匹配应失败");

        FakeHandler plain = new FakeHandler(store, false);
        assertFalse(plain.handle(new String[]{"-d", "1"}), "普通命令不支持的操作应返回失败");
        assertFalse(plain.handle(new String[]{"reload"}));

        FakeHandler noStore = new FakeHandler(null, true);
        assertFalse(noStore.handle(new String[0]), "存储未初始化应返回失败");
    }

    @Test
    void bangRequestRecognition() {
        // 只有"单 token 的 ! 输入"才是展开请求；聊天里 !gg / !hello world / 孤立 ! 必须放行
        assertTrue(HistoryCommandHandler.isBangRequest("!!"));
        assertTrue(HistoryCommandHandler.isBangRequest("!5"));
        assertTrue(HistoryCommandHandler.isBangRequest("!-2"));
        assertTrue(HistoryCommandHandler.isBangRequest("!tp"));
        assertFalse(HistoryCommandHandler.isBangRequest("!"));
        assertFalse(HistoryCommandHandler.isBangRequest("!gg world"));
        assertFalse(HistoryCommandHandler.isBangRequest("!hello world"));
        assertFalse(HistoryCommandHandler.isBangRequest("say hi"));
        assertFalse(HistoryCommandHandler.isBangRequest(null));
    }

    @Test
    void parseErrorShowsHelpButReportsFailure() {
        FakeHandler h = new FakeHandler(store(), true);
        assertFalse(h.handle(new String[]{"garbage"}), "参数看不懂要判失败（供命令方块/自动化感知）");
        assertTrue(h.messages.stream().anyMatch(m -> m.equals("historycli.help.title")),
                "但仍然要给用户看到帮助");
        assertTrue(h.handle(new String[]{"help"}), "显式 help 是成功");
    }

    @Test
    void refusesExpandingIntoOwnBangInvocation() {
        // 历史里出现 "historycliserver !1" 形态的行（被拒绝的调用也会被记录）时，
        // 展开结果不以 ! 开头，但派发回本命令会再次展开 → 必须在 common 层挡住
        HistoryStore store = new HistoryStore(tempDir.resolve("unused"));
        store.setMaxSize(0);
        store.add("historycliserver !1");
        store.add("say hi");

        FakeHandler h = new FakeHandler(store, true);
        h.handle(new String[]{"!1"});

        assertTrue(h.executed.isEmpty(), "不得把 historyxxx !n 再次派发出去（会无限递归）");
        assertTrue(h.errors.contains("historycli.msg.no_match !1"));
    }

    @Test
    void allowsExpandingIntoNormalTwoTokenCommands() {
        HistoryStore store = new HistoryStore(tempDir.resolve("unused"));
        store.setMaxSize(0);
        store.add("gamerule doDaylightCycle false");

        FakeHandler h = new FakeHandler(store, true);
        h.handle(new String[]{"!!"});

        assertEquals(java.util.List.of("gamerule doDaylightCycle false"), h.executed);
    }

    @Test
    void defaultRowHookKeepsLegacyFormatting() {
        // 只覆盖无序号 sendRow(String) 的平台（fabric/velocity/bungee/bukkit），
        // 输出必须仍是「序号 + 两空格 + 命令」——paper 端依赖这个默认委托保持两端一致
        FakeHandler h = new FakeHandler(store(), true);
        assertTrue(h.handle(new String[]{"list"}));
        assertEquals(java.util.List.of(
                "1  /tp @s 0 100 0",
                "2  say hello",
                "3  summon zombie"), h.messages);
    }

    @Test
    void indexedRowHookReceivesIndexAndCommandSeparately() {
        // paper 端要按序号拼「点击重跑」的命令，所以序号与命令必须分开下发
        List<Integer> indices = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<String> plainRows = new ArrayList<>();

        HistoryCommandHandler h = new HistoryCommandHandler(store(), true) {
            @Override
            protected void sendMessage(String key, Object... args) {
            }

            @Override
            protected void sendError(String key, Object... args) {
            }

            @Override
            protected void sendRow(String text) {
                plainRows.add(text);
            }

            @Override
            protected void sendRow(int index, String command) {
                indices.add(index);
                commands.add(command);
            }

            @Override
            protected void executeCommand(String command) {
            }
        };

        assertTrue(h.handle(new String[]{"list"}));
        assertEquals(java.util.List.of(1, 2, 3), indices);
        assertEquals(java.util.List.of("/tp @s 0 100 0", "say hello", "summon zombie"), commands);
        assertTrue(plainRows.isEmpty(), "覆盖了带序号的重载后不应再走无序号路径");
    }
}
