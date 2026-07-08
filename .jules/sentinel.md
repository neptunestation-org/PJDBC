## 2026-07-08 - Robust SQL Comment Handling in Security Filters
**Vulnerability:** SQL security filters using simple regex anchors (like `^\s*`) can be bypassed by prepending SQL comments (`/*...*/` or `--...`) to statements.
**Learning:** Standard whitespace anchors do not account for the diverse ways SQL engines handle comments. Additionally, DML operations can be nested within Common Table Expressions (CTEs), bypassing start-of-string keyword checks.
**Prevention:** Use a robust `PREFIX` and `SEP` regex that explicitly includes both block and line comments. Always use `Pattern.DOTALL` for multiline support. For read-only enforcement, specifically target keywords following `AS (` in CTEs.
