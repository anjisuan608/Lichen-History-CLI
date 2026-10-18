package org.anjisuan608.historycli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    void readKeepsUnflushedRowsDirty() throws IOException {
        // 修复前：-r 会把 dirtyCount 清零，之前录进去但还没落盘的行被永久标成"已写入"，
        // 随后的 -a 永远不会写出它们（单线程可复现的数据丢失）
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.add("pending-1");
        store.add("pending-2");
        assertTrue(store.isDirty());

        store.read();                 // 文件此时还不存在 → 只是保持不变
        assertTrue(store.isDirty(), "-r 之后未落盘的行仍然必须是脏的");

        Files.write(file(), List.of("from-file"), StandardCharsets.UTF_8);
        store.read();
        assertTrue(store.isDirty());

        store.append();
        assertEquals(List.of("from-file", "pending-1", "pending-2"), lines(file()));
        assertFalse(store.isDirty());
    }

    @Test
    void writeLeavesNoTempFileAndReplacesAtomically() throws IOException {
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.add("first");
        store.write();

        store.add("second");
        store.write();

        assertEquals(List.of("first", "second"), lines(file()));
        assertFalse(Files.exists(file().resolveSibling("command_history.log.tmp")),
                "临时文件应在原子移动后消失");
    }

    @Test
    void resolveLoneBangIsRejected() {
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.add("say hi");
        assertNull(store.resolve("!"), "孤立 `!` 不得等同于 `!!`，否则误敲感叹号会重跑上一条命令");
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

    /**
     * 回归：并发的 {@code write()} / {@code append()} 必须在<b>同一个临界区</b>里完成「取快照 → 落盘」。
     * <p>改动前是两段式（状态锁里取快照并清脏、{@code ioLock} 里写文件），两个写者会按与快照
     * <b>相反</b>的顺序落盘：文件被旧快照覆盖而 {@code dirtyCount} 已清 0（丢行、且标记为已写入），
     * 或 {@code append} 把 {@code write} 刚写过的行再追加一遍（重复行）。
     * 本用例两个线程的<b>最后一步都是落盘</b>，因此结束时的判据是确定性的：
     * 文件必须恰等于缓冲区、行不重复、且无未落盘行（返回 false 即说明存在未落盘行）。</p>
     * <p>注：旧实现是否触发竞态取决于调度，本用例对旧实现是<b>概率性</b>捕获，
     * 对新实现则恒成立 —— 作为「不得再把两段式写回来」的护栏。</p>
     */
    @Test
    void concurrentWriteAndAppendNeverLoseOrDuplicateRows() throws Exception {
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);   // 不裁剪，行数才能当判据

        int perThread = 400;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    store.add("w-" + i);
                    store.write();
                }
            });
            Future<?> appender = pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    store.add("a-" + i);
                    store.append();
                }
            });
            writer.get(60, TimeUnit.SECONDS);
            appender.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        List<String> onDisk = lines(file());
        assertEquals(store.snapshot(), onDisk, "文件应与缓冲区一致（旧快照回退=丢行）");
        assertEquals(onDisk.size(), new HashSet<>(onDisk).size(), "不应出现重复行");
        assertFalse(store.isDirty(), "两个线程最后一步都是落盘，不应残留未写行");
    }

    @Test
    void readStripsBomAndEmptyLines() throws IOException {
        // 记事本"另存为"会带 BOM；BOM 剥离后变空的首行、以及外部编辑留下的空行都不该进历史
        Files.writeString(file(), "\uFEFFalpha\n\nbeta\n", StandardCharsets.UTF_8);
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.read();
        assertEquals(List.of("alpha", "beta"), store.snapshot(), "BOM 与空行都不该进历史");
    }

    @Test
    void writeUsesLfOnly() throws IOException {
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.add("alpha");
        store.add("beta");
        store.write();
        String onDisk = new String(Files.readAllBytes(file()), StandardCharsets.UTF_8);
        assertEquals(-1, onDisk.indexOf('\r'),
                "落盘必须 LF-only：Files.write(Iterable) 会用平台行分隔符，Windows 上产 CRLF");
    }

    @Test
    void appendRepairsTruncatedLastLine() throws IOException {
        // 上次追加被中断：末尾是半行（没有换行符）。-a 必须先补 \n，否则新首行会与它拼成一行垃圾
        Files.writeString(file(), "alpha\npartial", StandardCharsets.UTF_8);
        HistoryStore store = new HistoryStore(file());
        store.setMaxSize(0);
        store.read();
        store.add("beta");
        store.append();
        assertEquals(List.of("alpha", "partial", "beta"), lines(file()), "半行应被补成独立一行");
    }
}
