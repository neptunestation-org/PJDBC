## 2026-05-11 - SQL Comment Bypass in Proxy Drivers
**Vulnerability:** Regex-based SQL filters (e.g., `ReadonlyDriver`, `SchemaValidationDriver`) could be bypassed by using SQL comments (`/*...*/` or `--...`) instead of whitespace. For example, `/* comment */ CREATE TABLE ...` bypassed patterns starting with `^\\s*`.
**Learning:** Standard `\\s` in Java regex does not match SQL comments, which databases often treat as whitespace. Attackers can use comments to hide keywords from security filters that rely on simple whitespace delimiters.
**Prevention:** Use a robust pattern that explicitly handles both whitespace and SQL comments: `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*`. Always use `Pattern.DOTALL` to ensure `.` matches newlines in multiline comments.
