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
 */
public final class HistoryStore {

    private final Path file;
    private final List<String> buffer = new ArrayList<>();
    private int dirtyStart = 0;
    private int maxSize = 500;
    private boolean ignoreDups = true;

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
     */
    public synchronized void add(String command) {
        if (command == null || command.isEmpty()) {
            return;
        }
        if (ignoreDups && !buffer.isEmpty() && buffer.get(buffer.size() - 1).equals(command)) {
            return;
        }
        buffer.add(command);
        trim();
    }

    public synchronized void setMaxSize(int maxSize) {
        this.maxSize = Math.max(0, maxSize);
        trim();
    }

    public synchronized void setIgnoreDups(boolean ignoreDups) {
        this.ignoreDups = ignoreDups;
    }

    public synchronized void clear() {
        buffer.clear();
        dirtyStart = 0;
    }

    /** -r：从文件追加读取到缓冲区末尾（bash 实际语义，不清空当前行）。 */
    public synchronized void read() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                buffer.add(line);
            }
            trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    /** -w：用缓冲区全量覆盖写入文件。 */
    public synchronized void write() {
        if (file == null) {
            return;
        }
        try {
            Files.write(file, buffer, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            dirtyStart = buffer.size();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

    /** -a：将内存中未写入的行追加到文件。 */
    public synchronized void append() {
        if (file == null) {
            return;
        }
        try {
            List<String> pending = new ArrayList<>(buffer.subList(Math.min(dirtyStart, buffer.size()), buffer.size()));
            Files.write(file, pending, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            dirtyStart = buffer.size();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append " + file, e);
        }
    }

    /** -c：清空缓冲区（不写文件）。 */
    public synchronized void reset() {
        clear();
    }

    /** -d：删除指定编号（1 起始）的单条。 */
    public synchronized boolean delete(int index) {
        if (index < 1 || index > buffer.size()) {
            return false;
        }
        buffer.remove(index - 1);
        dirtyStart = Math.min(dirtyStart, index - 1);
        return true;
    }

    private void trim() {
        if (maxSize <= 0 || buffer.size() <= maxSize) {
            return;
        }
        int overflow = buffer.size() - maxSize;
        buffer.subList(0, overflow).clear();
        dirtyStart = Math.max(0, dirtyStart - overflow);
    }
}
