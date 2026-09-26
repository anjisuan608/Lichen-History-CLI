package org.anjisuan608.historycli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 命令历史的内存缓冲区与文件读写（bash HISTFILE + 内存模型）。
 * <p>纯 Java 实现，不依赖任何平台 API，供各平台适配层复用。</p>
 *
 * <p>三个并发要点：</p>
 * <ol>
 *   <li><b>状态锁</b>：{@code buffer}/{@code dirtyCount} 的读写都在 {@code synchronized(this)} 内；</li>
 *   <li><b>文件锁 {@link #ioLock}</b>：{@code read/write/append} 三个文件操作互斥——
 *       它们在状态锁<b>之外</b>执行（避免磁盘 IO 阻塞命令线程），若不加锁，
 *       代理端定时落盘线程与命令线程的 {@code TRUNCATE_EXISTING} + {@code APPEND} 会交错，
 *       产生 NUL 空洞/重复尾行；</li>
 *   <li><b>{@code -w} 原子替换</b>：先写 {@code *.tmp} 再 {@code ATOMIC_MOVE}，
 *       崩溃不会留下被截断一半的日志（这是唯一的副本）。</li>
 * </ol>
 *
 * <p>落盘跟踪用「<b>未写入行数</b>」（{@code dirtyCount}，指缓冲区<b>末尾</b>那几行）。
 * 用行数而不是下标，是因为下标在 {@link #delete} 之后会错位；
 * 相应地所有操作都必须保持「脏行在尾部」这一不变量——{@link #read} 因此把文件行
 * <b>插到头部</b>而不是追加到尾部。</p>
 */
public final class HistoryStore {

    private final Path file;
    private final List<String> buffer = new ArrayList<>();
    /** 缓冲区末尾尚未写入文件的行数；0 表示全部已落盘。 */
    private int dirtyCount = 0;
    private int maxSize = 500;
    private boolean ignoreDups = true;
    private boolean recordEnabled = true;

    /** 文件操作互斥锁（独立于状态锁：状态锁只保护内存结构）。 */
    private final Object ioLock = new Object();

    public HistoryStore(Path file) {
        this.file = file;
    }

    public synchronized int size() {
        return buffer.size();
    }

    public synchronized List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(buffer));
    }

    public synchronized String get(int index) {
        if (index < 1 || index > buffer.size()) {
            return null;
        }
        return buffer.get(index - 1);
    }

    /**
     * 追加一条命令；连续重复时忽略（类似 bash ignoredups）。
     * 记录开关（recordEnabled=false）时跳过追加。
     */
    public synchronized void add(String command) {
        if (command == null || command.isEmpty() || !recordEnabled) {
            return;
        }
        if (ignoreDups && !buffer.isEmpty() && buffer.get(buffer.size() - 1).equals(command)) {
            return;
        }
        buffer.add(command);
        dirtyCount++;
        trim();
    }

    /**
     * 平台在把一条 {@code !} 展开命令交给处理器之前已经把它记入了历史；
     * 本方法在解析前把这条「本次调用自身」从缓冲区末尾剔除，
     * 否则 {@code !!} / {@code !-1} / {@code !n}（n==size）会解析到自己，
     * 执行后再次进入处理器形成无限递归（StackOverflowError）。
     *
     * @param bang 用户输入的展开表达式（如 {@code !!}、{@code !-1}、{@code !5}）
     * @return 是否剔除了末尾条目
     */
    public synchronized boolean dropSelfInvocation(String bang) {
        if (bang == null || bang.isEmpty() || buffer.isEmpty()) {
            return false;
        }
        String last = buffer.get(buffer.size() - 1);
        // 记录的原始行形如 "historyserver !!"、"/historyserver !!"、"!!"，
        // 即「命令名 + 空格 + 表达式」或「表达式本身」。
        if (!last.equals(bang) && !last.endsWith(" " + bang)) {
            return false;
        }
        buffer.remove(buffer.size() - 1);
        if (dirtyCount > 0) {
            dirtyCount--;
        }
        return true;
    }

    /** 是否记录新命令（默认 true）。 */
    public synchronized void setRecordEnabled(boolean recordEnabled) {
        this.recordEnabled = recordEnabled;
    }

    /** 当前是否记录新命令。 */
    public synchronized boolean recordEnabled() {
        return recordEnabled;
    }

    public synchronized void setMaxSize(int maxSize) {
        this.maxSize = Math.max(0, maxSize);
        trim();
    }

    public synchronized int maxSize() {
        return maxSize;
    }

    public synchronized void clear() {
        buffer.clear();
        dirtyCount = 0;
    }

    /**
     * -r：从文件读取历史。文件内容<b>插到缓冲区头部</b>（而不是追加到尾部）：
     * 文件里的行在时间上早于内存中尚未落盘的新行，且这样能保持
     * 「脏行集中在尾部」的不变量——否则 {@code dirtyCount} 无法表达，
     * 会像修复前那样把未落盘的行一并标成"已写入"，随后 {@code -a} 永远不会写出它们。
     */
    public void read() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        List<String> lines;
        synchronized (ioLock) {
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + file, e);
            }
        }
        synchronized (this) {
            buffer.addAll(0, lines);
            trim();
            dirtyCount = Math.min(dirtyCount, buffer.size());
        }
    }

    /**
     * -w：用缓冲区全量覆盖写入文件（原子替换：先写临时文件再移动）。
     * <p>状态标记在锁内先置 0，磁盘写在 {@link #ioLock} 内、状态锁外；
     * 失败时<b>加性</b>回滚（{@code += previous}）——用赋值会吞掉 IO 期间并发 {@code add} 的新行。</p>
     */
    public void write() {
        if (file == null) {
            return;
        }
        List<String> snapshot;
        int previous;
        synchronized (this) {
            snapshot = new ArrayList<>(buffer);
            previous = dirtyCount;
            dirtyCount = 0;
        }
        try {
            synchronized (ioLock) {
                writeAtomically(snapshot);
            }
        } catch (IOException e) {
            synchronized (this) {
                dirtyCount += previous;
            }
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

    /** -a：将内存中未写入的行追加到文件。 */
    public void append() {
        if (file == null) {
            return;
        }
        List<String> pending;
        int previous;
        synchronized (this) {
            previous = dirtyCount;
            pending = new ArrayList<>(buffer.subList(Math.max(0, buffer.size() - dirtyCount), buffer.size()));
            dirtyCount = 0;
        }
        if (pending.isEmpty()) {
            return;
        }
        try {
            synchronized (ioLock) {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(file, pending, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            synchronized (this) {
                dirtyCount += previous;
            }
            throw new UncheckedIOException("Failed to append " + file, e);
        }
    }

    /** 写临时文件后原子替换，避免「先截断再写」的崩溃窗口把整份日志毁掉。 */
    private void writeAtomically(List<String> lines) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 某些文件系统（跨盘/旧 NFS）不支持原子移动，退化为普通替换
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** -c：清空缓冲区（不写文件）。 */
    public synchronized void reset() {
        clear();
    }

    /**
     * -d：删除指定编号（1 起始）的单条。
     * <p>被删的行若尚未写入，未写计数同步减一；若已在文件里，文件侧保留该行（追加式日志的固有行为），
     * 需要用 -w 全量重写才能同步（与 bash 的 -d / -a 组合一致）。</p>
     */
    public synchronized boolean delete(int index) {
        if (index < 1 || index > buffer.size()) {
            return false;
        }
        int idx = index - 1;
        boolean wasDirty = idx >= buffer.size() - dirtyCount;
        buffer.remove(idx);
        if (wasDirty && dirtyCount > 0) {
            dirtyCount--;
        }
        dirtyCount = Math.min(dirtyCount, buffer.size());
        return true;
    }

    /** 当前是否有尚未写入文件的行（供定时落盘判断用）。 */
    public synchronized boolean isDirty() {
        return dirtyCount > 0;
    }

    /**
     * 解析 bash 历史展开表达式（!! / !n / !-n / !string），返回匹配的命令；无匹配返回 null。
     * 大小写敏感。
     *
     * <p>单独一个 {@code !} 一律按「无匹配」处理——bash 对孤立 {@code !} 的语义是
     * "event not found"，而把它当 {@code !!} 用会让聊天框里一个误敲的感叹号
     * 直接重跑上一条（可能是 {@code /stop} 之类的破坏性命令）。</p>
     *
     * <p>超长数字（如 {@code !999999999999999}）按「无匹配」处理，不抛 {@link NumberFormatException}。</p>
     */
    public synchronized String resolve(String expr) {
        if (expr == null || !expr.startsWith("!")) {
            return null;
        }
        if (expr.equals("!")) {
            return null;
        }
        if (expr.equals("!!")) {
            return buffer.isEmpty() ? null : buffer.get(buffer.size() - 1);
        }
        if (expr.length() < 2) {
            return null;
        }
        String body = expr.substring(1);
        if (isDigits(body)) {
            Integer idx = parseIndex(body);
            if (idx == null || idx < 1 || idx > buffer.size()) {
                return null;
            }
            return buffer.get(idx - 1);
        }
        if (body.length() > 1 && body.charAt(0) == '-' && isDigits(body.substring(1))) {
            Integer n = parseIndex(body.substring(1));
            if (n == null || n < 1 || n > buffer.size()) {
                return null;
            }
            return buffer.get(buffer.size() - n);
        }
        for (int i = buffer.size() - 1; i >= 0; i--) {
            if (buffer.get(i).startsWith(body)) {
                return buffer.get(i);
            }
        }
        return null;
    }

    private static boolean isDigits(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    /** 超出 int 范围时返回 null 而不是抛异常。 */
    private static Integer parseIndex(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void trim() {
        if (maxSize <= 0 || buffer.size() <= maxSize) {
            dirtyCount = Math.min(dirtyCount, buffer.size());
            return;
        }
        int overflow = buffer.size() - maxSize;
        buffer.subList(0, overflow).clear();
        // 只从头部丢弃，未写行仍在尾部；计数不得超过剩余长度
        dirtyCount = Math.min(dirtyCount, buffer.size());
    }
}
