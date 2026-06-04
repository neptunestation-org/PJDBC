## 2026-06-04 - Leading Comment & CTE Bypasses in SQL Filters
**Vulnerability:** Regex-based SQL security filters anchored to the start of the string (using `^`) can be bypassed by placing SQL comments (e.g., `/* comment */`) at the beginning of the query. Additionally, DML operations can be nested within Common Table Expressions (CTEs) or subqueries (e.g., `WITH x AS (DELETE ...)`), which bypass filters that only check the primary statement type at the start of the string.
**Learning:** Standard `String.trim()` or simple `\s*` regexes do not account for SQL-native comments. Filters must explicitly account for all valid SQL whitespace/comment combinations. Anchoring a filter only to the start of a string is insufficient for complex SQL that allows side-effect-producing nested statements.
**Prevention:**
1. Use a robust `PREFIX` regex that accounts for both whitespace and comments (both block `/*...*/` and line `--...`).
2. Use `Pattern.DOTALL` when matching comments to ensure multi-line comments are correctly handled.
3. Supplement start-of-statement checks with deep-scan regexes (`CTE_DML_PATTERN`) that look for DML keywords preceded by structural markers like `AS (` or typical SQL separators.
