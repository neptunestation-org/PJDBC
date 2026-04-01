## 2026-04-01 - SQL Comment Bypass Protection
**Vulnerability:** SQL-parsing logic in `ReadonlyDriver`, `SchemaValidationDriver`, and `WhereTransformer` used naive whitespace matching (`\s`), which allowed bypasses using SQL comments (e.g., `/* comment */ INSERT...` instead of `INSERT...`).
**Learning:** Security-focused regexes for SQL must account for comments as valid separators. Standard `\s+` is insufficient for robust keyword detection in SQL.
**Prevention:** Use a centralized `SqlPatterns` utility that defines a "SQL separator" as either whitespace OR a comment (block or line), and apply this consistently across all SQL-parsing regexes.
