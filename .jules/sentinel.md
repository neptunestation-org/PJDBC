## 2026-02-26 - [Bypass of SQL Parsing via Comments]
**Vulnerability:** SQL-based security controls (like `SchemaValidationDriver`, `SchemaTransformer`, and `WhereTransformer`) can be bypassed by using SQL comments (`/**/` or `--`) as separators instead of standard whitespace.
**Learning:** Standard regex patterns using `\\s+` fail to match SQL where keywords are separated by comments, allowing malicious or unauthorized queries to skip validation or transformation layers.
**Prevention:** Centralize SQL separator regexes to include both whitespace and all supported comment styles, and use these patterns consistently across all security-sensitive SQL parsing logic.
