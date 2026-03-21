## 2026-03-21 - Security bypass via SQL comments
**Vulnerability:** Regex-based SQL security filters (like ReadonlyDriver and SchemaValidationDriver) could be bypassed by using SQL comments (/* ... */ or -- ...) as separators instead of whitespace. For example, "/* bypass */ DELETE FROM table" would not match a pattern anchored with "^\\s*DELETE".
**Learning:** Naive regexes that assume whitespace as the only possible separator between SQL keywords and identifiers are inherently vulnerable to comment-based bypasses.
**Prevention:** Use a centralized, comment-aware separator regex (e.g., SqlPatterns.SQL_SEP) for all security-critical SQL parsing and transformation.
