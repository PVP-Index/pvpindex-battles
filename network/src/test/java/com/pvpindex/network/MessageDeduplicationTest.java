package com.pvpindex.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageDeduplicationTest {

    @Test
    void firstOccurrenceIsNotDuplicate() {
        MessageDeduplicator dedup = new MessageDeduplicator(100, 60_000);
        assertFalse(dedup.isDuplicate("msg-1"));
    }

    @Test
    void secondOccurrenceIsDuplicate() {
        MessageDeduplicator dedup = new MessageDeduplicator(100, 60_000);
        assertFalse(dedup.isDuplicate("msg-1"));
        assertTrue(dedup.isDuplicate("msg-1"));
    }

    @Test
    void differentIdsAreNotDuplicate() {
        MessageDeduplicator dedup = new MessageDeduplicator(100, 60_000);
        assertFalse(dedup.isDuplicate("msg-1"));
        assertFalse(dedup.isDuplicate("msg-2"));
    }

    @Test
    void evictsOldestWhenMaxSizeExceeded() {
        MessageDeduplicator dedup = new MessageDeduplicator(3, 60_000);
        dedup.isDuplicate("a");
        dedup.isDuplicate("b");
        dedup.isDuplicate("c");
        assertEquals(3, dedup.size());

        dedup.isDuplicate("d");
        assertFalse(dedup.isDuplicate("a"), "oldest entry should have been evicted");
    }

    @Test
    void clearRemovesAll() {
        MessageDeduplicator dedup = new MessageDeduplicator(100, 60_000);
        dedup.isDuplicate("a");
        dedup.isDuplicate("b");
        assertEquals(2, dedup.size());

        dedup.clear();
        assertEquals(0, dedup.size());
        assertFalse(dedup.isDuplicate("a"));
    }

    @Test
    void expiredEntriesAreEvicted() throws InterruptedException {
        MessageDeduplicator dedup = new MessageDeduplicator(100, 50);
        assertFalse(dedup.isDuplicate("msg-1"));

        Thread.sleep(100);

        assertFalse(dedup.isDuplicate("msg-1"), "expired entry should no longer be considered duplicate");
    }
}
