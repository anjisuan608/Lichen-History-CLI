package org.anjisuan608.historycli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 针对历史存储落盘语义（dirtyStart / -w / -a / -r）与展开安全性的回归测试。
 * <p>这些正是改动前会出问题的地方：-r 之后 -a 会把整个文件重复追加，
 * 超长数字的 !n 会抛 NumberFormatException，平台预记录的 bang 调用会让 !! 解析到自己。</p>
 */
class HistoryStoreIoTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("command_history.log");
    }

    private static List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    @Test
    void writeThenAppendOnlyWritesNewLines() throws IOException {
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.add("a");
        store.add("b");
        store.write();

        store.add("c");
        store.append();

        assertEquals(List.of("a", "b", "c"), lines(file()));
    }

    @Test
    void readMarksBufferCleanSoAppendDoesNotDuplicateFile() throws IOException {
        Files.write(file(), List.of("a", "b"), StandardCharsets.UTF_8);

        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.read();
        assertFalse(store.isDirty(), "-r 之后应视为已落盘，否则 -a 会重复追加");

        store.append();
        assertEquals(List.of("a", "b"), lines(file()));

        store.add("c");
        assertTrue(store.isDirty());
        store.append();
        assertEquals(List.of("a", "b", "c"), lines(file()));
    }

    @Test
    void appendWithoutPendingLinesDoesNotTouchFile() {
        Path file = file();
        HistoryStore store = new HistoryStore(file);
        store.setMaxSize(0);
        store.append();
        assertFalse(Files.exists(file), "没有待写行时不应创建文件");
    }

    @Test
    void failedWriteRollsBackDirtyFlag() throws IOException {
        // 用目录占住文件路径，让写入失败
        Path blocking = dir.resolve("blocked");
        Files.createDirectory(blocking);
        HistoryStore store = new HistoryStore(blocking);
        store.setMaxSize(0);
        store.add("a");

        assertThrows(UncheckedIOException.class, store::write);
        assertTrue(store.isDirty(), "写失败必须回滚标记，否则这些行永远不会被写入");
    }

    @Test
    void deleteInvalidatesFromThatIndex() throws IOException {
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.add("a");
        store.add("b");
        store.add("c");
        store.write();

        assertTrue(store.delete(2));
        store.add("d");
        store.append();

        // 文件是追加式的：删除无法回溯，但之后新增的行必须能追加上
        assertEquals(List.of("a", "b", "c", "d"), lines(file()));
        assertFalse(store.isDirty());
    }

    @Test
    void resolveOverflowReturnsNullInsteadOfThrowing() {
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.add("only");

        assertNull(store.resolve("!99999999999999999999"));
        assertNull(store.resolve("!-99999999999999999999"));
        assertEquals("only", store.resolve("!1"));
    }

    @Test
    void dropSelfInvocationRemovesOnlyTheTrailingBangForm() {
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.add("say hello");
        store.add("historyserver !!");   // 平台在进入处理器前记录的本次调用

        assertTrue(store.dropSelfInvocation("!!"));
        assertEquals("say hello", store.resolve("!!"));
        assertEquals(1, store.size(), "本次 bang 调用本身不应留在历史里");

        // 没有预记录时不应误删
        assertFalse(store.dropSelfInvocation("!!"));
        assertEquals(1, store.size());
    }

    @Test
    void trimKeepsDirtyStartConsistent() throws IOException {
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(3);
        store.add("a");
        store.add("b");
        store.add("c");
        store.write();
        store.add("d");   // 触发 trim，缓冲区丢掉 "a"，dirtyStart 同步前移
        store.add("e");

        store.append();
        // 文件保留已写入的历史，追加的是缓冲区里新增的部分
        assertEquals(List.of("a", "b", "c", "d", "e"), lines(file()));
        assertFalse(store.isDirty());

        // -w 用当前缓冲区全量覆盖，此时只剩最近 3 条
        store.write();
        assertEquals(List.of("c", "d", "e"), lines(file()));
    }
}
