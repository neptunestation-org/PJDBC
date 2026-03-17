## 2026-03-17 - [Comment-aware SQL regex parsing]
**Vulnerability:** Security-focused regex patterns (e.g., `^\\s*`) were bypassed by placing SQL comments (`/**/` or `--`) at the start of statements or between keywords and identifiers.
**Learning:** Standard whitespace matches (`\\s`) do not account for SQL comments, which are valid separators. Proxy drivers like `ReadonlyDriver` and `SchemaValidationDriver` were vulnerable to simple comment-based bypasses.
**Prevention:** Use centralized, comment-aware SQL separator regexes (`SqlPatterns.SQL_SEP` and `SQL_SEP_OPT`) and ensure statement-detection patterns are anchored and account for leading comments.
