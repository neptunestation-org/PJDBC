## 2026-03-29 - SQL Comment Bypass Protection
**Vulnerability:** SQL filtering drivers (Readonly, SchemaValidation) and transformers (Where, Schema) could be bypassed by using SQL comments (`/* ... */` or `-- ...`) instead of whitespace.
**Learning:** Naive regexes using `\s+` or `^\s*` are insufficient for SQL parsing because most databases treat comments as valid separators between keywords and identifiers.
**Prevention:** Use a centralized, comment-aware SQL separator regex (like `SqlPatterns.SQL_SEP`) for all security-critical SQL parsing.
