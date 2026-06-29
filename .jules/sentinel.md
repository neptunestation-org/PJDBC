## 2026-06-29 - [Harden ReadonlyDriver against SQL Comment and CTE Bypasses]
**Vulnerability:** ReadonlyDriver used simple `^\\s*` regex anchors which could be bypassed by leading SQL comments (e.g., `/*...*/ INSERT`). It also lacked detection for DML nested within CTEs (e.g., `WITH x AS (DELETE...)`).
**Learning:** Security filters based on simple regex string starts are easily bypassed in SQL due to the flexibility of whitespace and comment placement.
**Prevention:** Use a robust `PREFIX` regex that handles all types of SQL comments and whitespace, use `Pattern.DOTALL`, and explicitly check for DML keywords in `AS (...)` clauses.
