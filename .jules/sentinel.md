## 2026-02-16 - SQL Comment Bypass in Regex Parsing
**Vulnerability:** Security-sensitive drivers (ReadonlyDriver, SchemaValidationDriver) using simple `\s+` or `^\s*` regex patterns for SQL keyword detection were bypassable by using SQL comments (e.g., `/* comment */ INSERT` or `INSERT/**/INTO`).
**Learning:** Standard Java regex `\s` does not match SQL comments. Attackers can exploit this to hide keywords from security filters while still having them executed by the underlying database.
**Prevention:** Use a centralized, robust `SqlPatterns` utility that explicitly handles whitespace, block comments, and line comments. Always use `Pattern.DOTALL` when matching SQL that might contain multi-line comments.
