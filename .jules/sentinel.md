## 2026-05-31 - ReadonlyDriver SQL Bypass via Comments and CTEs
**Vulnerability:** ReadonlyDriver's SQL security filter could be bypassed by prefixing DML/DDL statements with SQL comments (e.g., `/* comment */ INSERT...`) or by wrapping them in Common Table Expressions (CTEs) like `WITH x AS (INSERT...) SELECT 1`.
**Learning:** Simple anchored regexes (e.g., `^\s*INSERT`) are insufficient for SQL security as they do not account for SQL comments which are treated as whitespace by many databases. Additionally, DML can be embedded in complex statements like CTEs.
**Prevention:** Use a centralized `SqlPatterns.PREFIX` that robustly skips both block and line comments. Harden DML detection regexes to explicitly look for keywords even after a `WITH` clause.
