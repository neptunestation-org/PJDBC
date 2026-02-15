## 2026-02-15 - SQL Comment Bypass in Security Drivers
**Vulnerability:** `ReadonlyDriver` and `SchemaValidationDriver` used simple `\s+` or `^\s*` patterns in their SQL parsing regexes. This allowed bypasses by placing SQL comments (`/*...*/` or `--...`) where whitespace was expected.
**Learning:** SQL comments are valid separators in most databases and can be used to evade naive regex-based security filters that only look for whitespace.
**Prevention:** Always use a comment-aware separator pattern like `(?:\s+|/\*.*?\*/|--[^\r\n]*)*` when parsing SQL with regexes, and use `Pattern.DOTALL` to handle multi-line block comments.
