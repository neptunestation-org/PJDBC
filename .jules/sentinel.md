## 2026-06-30 - SQL Comment and CTE Bypass in ReadonlyDriver
**Vulnerability:** ReadonlyDriver and SchemaValidationDriver could be bypassed by using SQL comments (e.g., `/* comment */ DELETE ...`) or Common Table Expressions with nested DML (e.g., `WITH x AS (DELETE ...) SELECT ...`).
**Learning:** Simple regex anchors (`^\s*`) and keyword matches at the start of a string are insufficient for SQL security filtering because SQL allows comments and complex structures like CTEs that can hide DML operations.
**Prevention:** Use a robust prefix regex `(?:\s|/\*.*?\*/|--[^\n]*?(?:\n|$))*` with `Pattern.DOTALL` to skip comments/whitespace, and explicitly look for nested DML keywords within CTE structures.
