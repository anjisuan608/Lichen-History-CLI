package org.anjisuan608.historycli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 命令历史的内存缓冲区与文件读写（bash HISTFILE + 内存模型）。
 * <p>纯 Java 实现，不依赖任何平台 API，供各平台适配层复用。</p>
 *
 * <p>线程模型：状态读写均为 {@code synchronized}，但整文件 IO 已移出锁外
 * （先在锁内取快照/落标记，再在锁外读写磁盘），避免在锁内做磁盘 IO 阻塞主线程/网络线程。</p>
 *
 * <p>落盘跟踪用「<b>未写入行数</b>」而不是「首条未写入下标」：下标在 {@link #delete} 之后会错位，
 * 导致 {@code -a} 把已经在文件里的行再追加一遍；计数模型下删除只是让计数减一（被删的若本就未写入），
 * 行为与 bash 的 {@code history -d} + {@code history -a} 一致。</p>
 */
public final class HistoryStore {

    private final Path file;
    private final List<String> buffer = new ArrayList<>();
    /** 缓冲区末尾尚未写入文件的行数；0 表示全部已落盘。 */
    private int dirtyCount = 0;
    private int maxSize = 500;
    private boolean ignoreDups = true;
    private boolean recordEnabled = true;

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
     * -r：从文件追加读取到缓冲区末尾（bash 实际语义，不清空当前行）。
     * <p>读入的行本就来自文件，因此读取后视为「已全部落盘」，
     * 否则随后的 -a 会把整个文件重复追加一遍。这与 bash 的 -r 语义一致。</p>
     */
    public void read() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
        synchronized (this) {
            buffer.addAll(lines);
            trim();
            dirtyCount = 0;
        }
    }

    /**
     * -w：用缓冲区全量覆盖写入文件。
     * <p>快照与「标记为已落盘」在锁内完成，磁盘写在锁外；写失败时回滚标记，
     * 保证未成功写入的行仍会被后续 -a 追加。</p>
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
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, snapshot, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            synchronized (this) {
                dirtyCount = previous;
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
            int start = Math.max(0, buffer.size() - dirtyCount);
            pending = new ArrayList<>(buffer.subList(start, buffer.size()));
            dirtyCount = 0;
        }
        if (pending.isEmpty()) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, pending, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException e) {
            synchronized (this) {
                dirtyCount = previous;
            }
            throw new UncheckedIOException("Failed to append " + file, e);
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
     * <p>纯数字与负数字分支对超长数字（如 {@code !999999999999999}）做了溢出保护，
     * 越界一律返回 null 而不是抛 {@link NumberFormatException}。</p>
     */
    public synchronized String resolve(String expr) {
        if (expr == null || !expr.startsWith("!")) {
            return null;
        }
        if (expr.equals("!") || expr.equals("!!")) {
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
