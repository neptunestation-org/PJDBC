## 2026-05-29 - SQL Comment-Based Security Bypass
**Vulnerability:** SQL security filters in `ReadonlyDriver` and `SchemaValidationDriver` used regex patterns that only accounted for whitespace (e.g., `^\\s*`) but not SQL comments (e.g., `/*...*/` or `--...`). This allowed attackers to bypass security checks by prefixing blocked statements with comments.
**Learning:** SQL parsing must always account for comments, which can appear anywhere whitespace is allowed. Simple regex that only looks for `\\s*` is insufficient for robust security filtering in JDBC proxies.
**Prevention:** Use a centralized utility (`SqlPatterns`) for SQL regex components that explicitly handles both types of SQL comments and multi-line statements (using `Pattern.DOTALL`).
