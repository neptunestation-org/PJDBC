## 2026-07-04 - ReadonlyDriver SQL Bypass
**Vulnerability:** ReadonlyDriver could be bypassed by prefixing DML/DDL/DCL statements with SQL comments (e.g., `/* bypass */ INSERT...`) or by nesting DML within Common Table Expressions (CTEs).
**Learning:** Regex anchors like `^\s*` are easily bypassed by SQL comments. JDBC drivers must robustly skip leading comments and handle multi-line statements with `Pattern.DOTALL`. CTEs introduce another layer where restricted keywords can be "hidden" after the `AS (` marker.
**Prevention:** Use a centralized or robust `PREFIX` regex: `(?:\\s|/\\*.*?\\*/|--[^\\n]*?(?:\\n|$))*`. Always use `Pattern.DOTALL` with multiline SQL regexes. Explicitly scan for restricted keywords within structural markers like CTE definitions.
