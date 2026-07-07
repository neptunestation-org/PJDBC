## 2026-07-07 - [ReadonlyDriver and SchemaValidationDriver SQL Comment Bypass]
**Vulnerability:** SQL security filters using simplistic `^\s*` or `\s+` regex anchors can be bypassed by placing SQL comments (`/*...*/` or `--...`) at the start of the statement or between keywords. For example, `/* bypass */ INSERT ...` would not match `^\\s*INSERT`.
**Learning:** Many databases allow comments almost anywhere in a SQL statement, including before the first keyword. Relying on whitespace-only regex anchors for security boundaries is insufficient.
**Prevention:** Use a robust `PREFIX` or `SEP` regex that explicitly accounts for both whitespace and SQL comments: `(?:\\s|/\\*.*?\\*/|--[^\\n]*?(?:\\n|$))*`. Always use `Pattern.DOTALL` to ensure multi-line comments are correctly handled.
