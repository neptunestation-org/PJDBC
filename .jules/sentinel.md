## 2026-06-28 - Harden ReadonlyDriver against SQL bypasses
**Vulnerability:** SQL comment-based and CTE-based bypasses in ReadonlyDriver.
**Learning:** Simple regex anchors (`^\s*`) are insufficient for SQL filtering because comments can precede keywords. CTEs allow DML (INSERT, UPDATE, DELETE) to be nested deep within a statement that starts with a safe keyword like `WITH`.
**Prevention:** Use a robust prefix regex (`(?:\s|/\*.*?\*/|--.*?(?:\n|$))*`) that handles all comment types at the start of statements. Use `Pattern.DOTALL` to ensure multi-line comments are handled. Specifically target structural markers like `AS (` followed by the prefix to detect nested DML in Common Table Expressions.
