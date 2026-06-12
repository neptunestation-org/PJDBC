## 2026-06-12 - SQL Parsing Evasion via Comments and CTEs
**Vulnerability:** Security drivers (ReadonlyDriver, SchemaValidationDriver) relied on simple regex or `trim()` to identify SQL keywords, which could be bypassed using SQL comments (e.g., `/* comment */ INSERT`) or Common Table Expressions (e.g., `WITH cte AS (...) INSERT ...`).
**Learning:** Naive SQL parsing is highly susceptible to evasion. `^\\s*` does not account for comments, and "starts-with" checks miss nested DML in CTEs.
**Prevention:** Use a robust `SqlPatterns.PREFIX` that handles comments and whitespace with `Pattern.DOTALL`. For CTEs, use structural anchors like `AS (` to detect nested operations. Always strip comments from identifiers before validation.
