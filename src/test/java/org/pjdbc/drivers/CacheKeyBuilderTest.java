package org.pjdbc.drivers;

import static org.junit.Assert.*;
import org.junit.Test;

public class CacheKeyBuilderTest {

    @Test
    public void sha256ProducesConsistentHash() {
        String hash1 = CacheKeyBuilder.sha256("SELECT * FROM users");
        String hash2 = CacheKeyBuilder.sha256("SELECT * FROM users");
        assertEquals(hash1, hash2);
    }

    @Test
    public void sha256Produces64CharHex() {
        String hash = CacheKeyBuilder.sha256("test");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }

    @Test
    public void sha256DifferentInputsProduceDifferentHashes() {
        String hash1 = CacheKeyBuilder.sha256("SELECT * FROM users");
        String hash2 = CacheKeyBuilder.sha256("SELECT * FROM orders");
        assertNotEquals(hash1, hash2);
    }

    @Test
    public void sha256HandlesNullInput() {
        String hash = CacheKeyBuilder.sha256(null);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    public void sha256HandlesEmptyInput() {
        String hash = CacheKeyBuilder.sha256("");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    public void buildKeyIncludesPrefix() {
        String key = CacheKeyBuilder.buildKey("pjdbc:", "SELECT 1");
        assertTrue(key.startsWith("pjdbc:"));
        assertEquals("pjdbc:".length() + 64, key.length());
    }

    @Test
    public void buildKeyWithParamsIncludesParams() {
        String key1 = CacheKeyBuilder.buildKey("cache:", "SELECT * FROM users WHERE id = ?", 1);
        String key2 = CacheKeyBuilder.buildKey("cache:", "SELECT * FROM users WHERE id = ?", 2);
        assertNotEquals(key1, key2);
    }

    @Test
    public void buildKeyWithSameParamsProducesSameKey() {
        String key1 = CacheKeyBuilder.buildKey("cache:", "SELECT * FROM users WHERE id = ?", 42);
        String key2 = CacheKeyBuilder.buildKey("cache:", "SELECT * FROM users WHERE id = ?", 42);
        assertEquals(key1, key2);
    }

    @Test
    public void buildKeyWithNullParamHandled() {
        String key = CacheKeyBuilder.buildKey("cache:", "SELECT * FROM users WHERE name = ?", (Object) null);
        assertNotNull(key);
        assertTrue(key.startsWith("cache:"));
    }

    @Test
    public void buildKeyWithMultipleParams() {
        String key = CacheKeyBuilder.buildKey("cache:", "SELECT * FROM users WHERE id = ? AND name = ?", 1, "Alice");
        assertNotNull(key);
        assertTrue(key.startsWith("cache:"));
    }

    @Test
    public void hashCollisionResistance() {
        // These strings have the same Java hashCode() but different SHA-256
        // "Aa" and "BB" have the same hashCode in Java
        String hash1 = CacheKeyBuilder.sha256("Aa");
        String hash2 = CacheKeyBuilder.sha256("BB");
        assertNotEquals("SHA-256 should differentiate strings with same Java hashCode", hash1, hash2);
    }
}
