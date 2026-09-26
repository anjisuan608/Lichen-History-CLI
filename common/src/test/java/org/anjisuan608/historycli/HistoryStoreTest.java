package org.anjisuan608.historycli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HistoryStoreTest {

    private HistoryStore store() {
        HistoryStore store = new HistoryStore(Path.of("unused"));
        store.setMaxSize(0);
        store.add("tp @s 0 100 0");
        store.add("say hello");
        store.add("summon zombie");
        return store;
    }

    @Test
    void bangDoubleBang() {
        assertEquals("summon zombie", store().resolve("!!"));
        // bash 对孤立 `!` 的语义是 event not found：不得把聊天框里误敲的感叹号
        // 变成"重跑上一条（可能是 /stop）"
        assertNull(store().resolve("!"));
    }

    @Test
    void bangNumber() {
        assertEquals("tp @s 0 100 0", store().resolve("!1"));
        assertEquals("say hello", store().resolve("!2"));
        assertNull(store().resolve("!5"));
    }

    @Test
    void bangNegativeNumber() {
        assertEquals("summon zombie", store().resolve("!-1"));
        assertEquals("say hello", store().resolve("!-2"));
    }

    @Test
    void bangPrefixCaseSensitive() {
        assertEquals("summon zombie", store().resolve("!summon"));
        assertEquals("summon zombie", store().resolve("!s"));
        assertNull(store().resolve("!Summon"));
    }

    @Test
    void bangNoMatch() {
        assertNull(store().resolve("!nonexistent"));
        assertNull(store().resolve(""));
    }

    @Test
    void consecutiveDupIgnored() {
        HistoryStore store = store();
        store.add("summon zombie");
        assertEquals(3, store.size());
        assertEquals("summon zombie", store.resolve("!3"));
    }
}
