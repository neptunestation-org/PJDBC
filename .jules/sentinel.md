# Sentinel's Journal - Critical Security Learnings

## 2026-06-25 - SQL Comment and CTE Bypass in ReadonlyDriver
**Vulnerability:** Regex-based SQL security filters using `^\\s*` anchors and failing to account for multi-line comments or nested DML within Common Table Expressions (CTEs).
**Learning:** Leading comments (`/*...*/` or `--...`) allow DML statements like `INSERT` or `DELETE` to bypass anchors that only expect whitespace. Additionally, CTEs like `WITH x AS (DELETE...)` hide DML operations from simple start-of-string filters.
**Prevention:** Use a robust `PREFIX` pattern that explicitly handles both block and line comments, enable `Pattern.DOTALL`, and add specific patterns for structural markers (like `AS (`) that might precede DML in nested contexts. Use capturing groups to accurately report the blocked keyword in security exceptions.
