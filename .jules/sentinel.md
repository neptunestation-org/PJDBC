## 2026-07-10 - SQL Comment-Based Security Bypass

**Vulnerability:** SQL-based security filters (e.g., `ReadonlyDriver`, `SchemaValidationDriver`) using simple `^\\s*` anchors or `\\s+` separators can be bypassed by placing SQL comments (`/*...*/` or `--...`) at the start of the statement or between keywords. Additionally, `ReadonlyDriver` was bypassed by DML operations nested inside Common Table Expressions (CTEs).

**Learning:** Regex-based SQL parsing is inherently fragile and requires explicit handling of all characters that the database considers "ignorable," including various comment styles and multi-line whitespace. `Pattern.DOTALL` is essential for matching multi-line comments.

**Prevention:** Use a centralized, robust `PREFIX` and `SEP` regex that explicitly includes comment patterns. For security-critical drivers like `ReadonlyDriver`, also scan for nested DML patterns (e.g., inside `WITH` clauses). Always verify security filters against actual databases with comment-injected payloads.
