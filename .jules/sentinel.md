## 2026-04-14 - SQL Comment Bypass in Regex Filters
**Vulnerability:** Security filters (ReadonlyDriver, SchemaValidationDriver) and SQL transformers (SchemaTransformer, WhereTransformer) could be bypassed by using SQL comments (`/*...*/` or `--...`) as whitespace/keyword separators.
**Learning:** SQL considers comments as equivalent to whitespace in many contexts. Standard regex patterns using `\s+` or `\b` may fail to match if a comment is used as a delimiter, allowing restricted keywords or table names to escape detection or transformation.
**Prevention:** Use a robust whitespace/comment pattern like `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+` combined with `Pattern.DOTALL` for all regex-based SQL parsing that enforces security policies.
