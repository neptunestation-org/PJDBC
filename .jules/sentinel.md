# Sentinel's Journal - Critical Security Learnings

## 2026-06-27 - SQL Comment and CTE Bypass in ReadonlyDriver
**Vulnerability:** Regex-based SQL security filters in `ReadonlyDriver` could be bypassed using leading SQL comments (e.g., `/* comment */ INSERT...`) or DML nested within Common Table Expressions (e.g., `WITH x AS (DELETE...) SELECT...`).
**Learning:** Simple start-of-string regex anchors (`^\\s*`) are insufficient for SQL parsing as they ignore the fact that comments can precede or be interspersed with keywords. Furthermore, modern SQL dialects allow side-effecting DML within CTEs which standard keyword-at-start checks miss.
**Prevention:** Use a robust `PREFIX` regex that explicitly handles all SQL comment styles (`/*...*/` and `--...`) with `Pattern.DOTALL`. Specifically target structural markers like `AS (` in CTEs to detect nested DML. Always use capturing groups to report the exact blocked keyword for better diagnostics.
