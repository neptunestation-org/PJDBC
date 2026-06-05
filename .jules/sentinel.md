## 2026-06-05 - SQL Comment and CTE Bypass in ReadonlyDriver
**Vulnerability:** Read-only enforcement was bypassed by leading comments or embedding DML in Common Table Expressions (CTEs).
**Learning:** Simple `^\\s*` regex anchors at the start of SQL strings are insufficient for security filtering as they don't account for SQL comments or nested statements.
**Prevention:** Use robust patterns that handle multi-line comments (`Pattern.DOTALL`) and look for blocked keywords at any valid SQL execution point (e.g., after `AS (` in CTEs or after a closing parenthesis), while carefully avoiding false positives in string literals.
