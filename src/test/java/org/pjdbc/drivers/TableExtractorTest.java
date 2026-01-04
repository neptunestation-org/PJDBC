package org.pjdbc.drivers;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

/**
 * Tests for TableExtractor SQL parsing.
 */
public class TableExtractorTest {

    @Test
    public void extractFromSimpleSelect() {
        Set<String> tables = TableExtractor.extractTables("SELECT * FROM users");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromSelectWithAlias() {
        Set<String> tables = TableExtractor.extractTables("SELECT u.id FROM users u");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromSelectWithAsAlias() {
        Set<String> tables = TableExtractor.extractTables("SELECT u.id FROM users AS u");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromSelectMultipleTables() {
        Set<String> tables = TableExtractor.extractTables("SELECT * FROM users, orders, products");
        assertEquals(3, tables.size());
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("orders"));
        assertTrue(tables.contains("products"));
    }

    @Test
    public void extractFromSelectWithJoin() {
        Set<String> tables = TableExtractor.extractTables(
            "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id");
        assertEquals(2, tables.size());
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("orders"));
    }

    @Test
    public void extractFromSelectWithMultipleJoins() {
        Set<String> tables = TableExtractor.extractTables(
            "SELECT * FROM users u " +
            "LEFT JOIN orders o ON u.id = o.user_id " +
            "INNER JOIN products p ON o.product_id = p.id " +
            "RIGHT JOIN categories c ON p.category_id = c.id");
        assertEquals(4, tables.size());
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("orders"));
        assertTrue(tables.contains("products"));
        assertTrue(tables.contains("categories"));
    }

    @Test
    public void extractFromInsert() {
        Set<String> tables = TableExtractor.extractTables("INSERT INTO users (name) VALUES ('Alice')");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromInsertWithoutInto() {
        Set<String> tables = TableExtractor.extractTables("INSERT users (name) VALUES ('Alice')");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromUpdate() {
        Set<String> tables = TableExtractor.extractTables("UPDATE users SET name = 'Bob' WHERE id = 1");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromDelete() {
        Set<String> tables = TableExtractor.extractTables("DELETE FROM users WHERE id = 1");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromTruncate() {
        Set<String> tables = TableExtractor.extractTables("TRUNCATE TABLE users");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromTruncateWithoutTable() {
        Set<String> tables = TableExtractor.extractTables("TRUNCATE users");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromDropTable() {
        Set<String> tables = TableExtractor.extractTables("DROP TABLE users");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromDropTableIfExists() {
        Set<String> tables = TableExtractor.extractTables("DROP TABLE IF EXISTS users");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromCreateTable() {
        Set<String> tables = TableExtractor.extractTables("CREATE TABLE users (id INT)");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromCreateTableIfNotExists() {
        Set<String> tables = TableExtractor.extractTables("CREATE TABLE IF NOT EXISTS users (id INT)");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromAlterTable() {
        Set<String> tables = TableExtractor.extractTables("ALTER TABLE users ADD COLUMN email VARCHAR(255)");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractFromMerge() {
        Set<String> tables = TableExtractor.extractTables("MERGE INTO users USING temp_users ON users.id = temp_users.id");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void handlesQuotedTableNames() {
        Set<String> tables = TableExtractor.extractTables("SELECT * FROM \"users\"");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void handlesBacktickedTableNames() {
        Set<String> tables = TableExtractor.extractTables("SELECT * FROM `users`");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void handlesBracketedTableNames() {
        Set<String> tables = TableExtractor.extractTables("SELECT * FROM [users]");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void handlesSchemaQualifiedTable() {
        Set<String> tables = TableExtractor.extractTables("SELECT * FROM public.users");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("public.users"));
    }

    @Test
    public void ignoresStringLiterals() {
        Set<String> tables = TableExtractor.extractTables("SELECT * FROM users WHERE name = 'FROM orders'");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
        assertFalse(tables.contains("orders"));
    }

    @Test
    public void ignoresSingleLineComments() {
        Set<String> tables = TableExtractor.extractTables("SELECT * FROM users -- FROM orders");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void ignoresBlockComments() {
        Set<String> tables = TableExtractor.extractTables("SELECT * FROM users /* FROM orders */");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void caseInsensitive() {
        Set<String> tables = TableExtractor.extractTables("select * from USERS");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
    }

    @Test
    public void extractTablesFromSelectOnly() {
        Set<String> tables = TableExtractor.extractTablesFromSelect("SELECT * FROM users JOIN orders ON users.id = orders.user_id");
        assertEquals(2, tables.size());
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("orders"));
    }

    @Test
    public void extractTablesFromWriteOnly() {
        Set<String> tables = TableExtractor.extractTablesFromWrite("INSERT INTO users SELECT * FROM temp_users");
        assertEquals(1, tables.size());
        assertTrue(tables.contains("users"));
        // Should not include temp_users since we're only looking at write targets
    }

    @Test
    public void emptyForNullSql() {
        Set<String> tables = TableExtractor.extractTables(null);
        assertTrue(tables.isEmpty());
    }

    @Test
    public void emptyForEmptySql() {
        Set<String> tables = TableExtractor.extractTables("");
        assertTrue(tables.isEmpty());
    }

    @Test
    public void emptyForWhitespaceSql() {
        Set<String> tables = TableExtractor.extractTables("   ");
        assertTrue(tables.isEmpty());
    }

    @Test
    public void normalizeTableName() {
        assertEquals("users", TableExtractor.normalizeTableName("users"));
        assertEquals("users", TableExtractor.normalizeTableName("\"users\""));
        assertEquals("users", TableExtractor.normalizeTableName("`users`"));
        assertEquals("users", TableExtractor.normalizeTableName("[users]"));
        assertEquals("users", TableExtractor.normalizeTableName("users u"));
        assertEquals("users", TableExtractor.normalizeTableName("users AS u"));
        assertEquals("", TableExtractor.normalizeTableName(null));
        assertEquals("", TableExtractor.normalizeTableName(""));
    }
}
