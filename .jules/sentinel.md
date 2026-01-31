# Sentinel Security Journal - PJDBC

## 2026-01-31 - Regex-based SQL Parsing Bypass in SchemaValidationDriver
**Vulnerability:** The `SchemaValidationDriver` used simple regular expressions to extract table and column names from SQL statements. These regexes failed to account for quoted identifiers (e.g., `"table_name"`, `` `table_name` ``, `[table_name]`). An attacker could bypass whitelist/blacklist restrictions by simply wrapping table or column names in quotes.
**Learning:** Security controls based on string pattern matching (like regex) are extremely difficult to get right for complex languages like SQL. Minor variations in syntax can lead to complete bypasses.
**Prevention:** Use robust, well-tested parsers for complex grammars. If regex must be used, ensure it handles all valid variations of the target patterns, including different quoting and escaping styles.
