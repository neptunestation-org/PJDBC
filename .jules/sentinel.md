## 2026-04-03 - [Leading Comment Bypass in SQL Parsing]
**Vulnerability:** Security-critical SQL regexes (DML/DDL detection) used `^\\s*`, allowing bypasses via leading SQL comments (e.g., `/* comment */ INSERT...`).
**Learning:** Standard whitespace matches in regex do not account for SQL comments, which databases often treat as valid separators before or between keywords.
**Prevention:** Always use a centralized, comment-aware separator pattern (like `SqlPatterns.SQL_SEP_OPT`) when anchoring SQL detection regexes at the start of a statement.
