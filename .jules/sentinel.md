## 2026-05-09 - SQL Comment-based Security Bypass in Regex Filters
**Vulnerability:** Security filters using regex (e.g., `ReadonlyDriver`, `SchemaValidationDriver`) could be bypassed by using SQL comments (`/*...*/` or `--...`) instead of standard whitespace delimiters.
**Learning:** SQL parsers treat comments as whitespace, but simple regexes using `\s+` or `^` don't account for them. Leading comments can bypass start-of-string anchors, and interspersed comments can break keyword/identifier matching.
**Prevention:** Use robust whitespace patterns that explicitly include SQL comments: `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+`. Always use `Pattern.DOTALL` to ensure `.` matches newlines in multi-line comments.
