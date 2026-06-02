## 2026-06-02 - SQL Comment-Based Security Bypass
**Vulnerability:** SQL security filters (ReadonlyDriver, SchemaValidationDriver, etc.) were bypassed using SQL comments (e.g., `SELECT/*...*/FROM`) because regexes only accounted for standard whitespace (`\s+`).
**Learning:** In SQL, comments can often be used anywhere whitespace is allowed. Simple whitespace-based regex boundaries are insufficient for security boundaries.
**Prevention:** Use a unified SQL pattern utility that defines a "separator" as any combination of whitespace AND comments (both `/*...*/` and `--...`). Always use `Pattern.DOTALL` when matching SQL to ensure multi-line comments are correctly handled.
