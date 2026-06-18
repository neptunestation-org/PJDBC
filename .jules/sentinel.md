## 2026-06-18 - [ReadonlyDriver] Comment and CTE Bypasses
**Vulnerability:** ReadonlyDriver could be bypassed by prefixing DML/DDL statements with SQL comments or by nesting DML within Common Table Expressions (CTEs).
**Learning:** Simple `startsWith` or `trim().indexOf()` checks are insufficient for SQL security as they don't account for SQL comments or complex query structures like CTEs.
**Prevention:** Use a centralized `SqlPatterns` utility with robust regexes that handle whitespace and comments (`Pattern.DOTALL`). Explicitly check for DML keywords within CTE structural markers (`AS (`, `)`).
