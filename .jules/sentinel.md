## 2026-05-13 - Hardening Regex-based SQL Security Filters
**Vulnerability:** SQL security filters (like `ReadonlyDriver`, `SchemaValidationDriver`, `SchemaTransformer`, and `WhereTransformer`) could be bypassed by using SQL comments (`/*...*/` or `--...`) in place of whitespace.
**Learning:** Standard regexes using `\\s+` or `^\\s*` fail to account for SQL comments, which are treated as whitespace by many database engines but not by simple regex patterns.
**Prevention:** Use a robust prefix/separator pattern like `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+` combined with `Pattern.DOTALL` to ensure comments are correctly identified as delimiters in security-critical regexes.
