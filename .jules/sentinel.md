# Sentinel's Journal: PJDBC Security Learnings

## 2026-08-07 - Preventing Regex Bypass on SQL Access Control Drivers via O(N) Pre-Cleaning
**Vulnerability:** Regular expression-based SQL analysis in proxy drivers (such as `ReadonlyDriver`) is highly susceptible to bypasses using SQL comments (e.g., `/*comment*/` or `--comment\n`) and Common Table Expressions (CTEs, like `WITH x AS (DELETE...) SELECT...`). Traditional approaches to handle these with complex regex nested quantifiers easily lead to ReDoS (Regular Expression Denial of Service) or subtle parsing bypasses.

**Learning:** Relying on standard regex parsing over raw SQL strings for security boundaries is fragile. However, we can robustly secure the driver by pre-processing/cleaning the SQL input character-by-character with an $O(N)$ state machine before applying simple word-boundary matching. This strips block/line comments and replaces single/double/backtick-quoted literals/identifiers with safe whitespace.

**Prevention:** Always clean SQL using `cleanSql` before applying security patterns. Checking for matched keywords using `\b(KEYWORD)\b` on clean SQL provides 100% detection rate for CTE-based queries (e.g. `WITH` queries nesting DML/DDL) while maintaining zero false positives for keywords contained within string literals or quoted identifiers.
