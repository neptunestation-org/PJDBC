## 2026-07-02 - SQL Comment and CTE Bypass in ReadonlyDriver

**Vulnerability:** SQL security filters relying on simple `^\\s*` anchors or not accounting for nested structures like Common Table Expressions (CTEs) can be bypassed using leading comments (`/*...*/`, `--`) or by wrapping DML inside a `WITH` clause.

**Learning:** Regex-based SQL parsing is fragile. A simple `^\\s*` only skips whitespace, but SQL allows comments before the first keyword. Furthermore, DML can be "hidden" inside CTEs (e.g., `WITH t AS (INSERT ...) SELECT ...`), which a start-of-string anchor will miss entirely.

**Prevention:** Use a robust `PREFIX` regex that accounts for all whitespace and both types of SQL comments. Apply `Pattern.DOTALL` to ensure `.` matches newlines in multiline comments. Supplement start-of-string checks with structural patterns (like `AS (...)`) to detect DML nested within CTEs. Always verify with negative tests specifically designed to bypass the filter.
