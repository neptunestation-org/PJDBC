# Sentinel's Journal - Critical Security Learnings

## 2026-07-01 - Regex-based SQL security bypasses via comments and CTEs
**Vulnerability:** SQL-based security filters in PJDBC (e.g., `ReadonlyDriver`, `SchemaValidationDriver`) were using simple regex anchors like `^\\s*` which could be bypassed by placing SQL comments (`/*...*/` or `--...`) at the start of a statement or between keywords. Furthermore, `ReadonlyDriver` did not detect DML nested within Common Table Expressions (CTEs) like `WITH x AS (DELETE ...) SELECT ...`.

**Learning:** When using regex to enforce security policies on SQL, you cannot rely on simple whitespace matching. SQL is highly flexible and allows comments almost anywhere. Blacklisting at the start of the string is insufficient when nested structures like CTEs can contain side-effecting operations.

**Prevention:** Use a robust `PREFIX` regex that explicitly matches both whitespace and all supported SQL comment styles. Use `Pattern.DOTALL` to ensure `.` matches newlines in multi-line comments. For side-effect detection, patterns must look into nested blocks (like CTE `AS (...)` blocks) rather than just the statement start. Always verify security filters with dedicated "bypass" test suites.
