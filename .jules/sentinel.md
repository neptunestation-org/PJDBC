## 2026-06-14 - Harden SQL Security Filters against Comment and CTE Bypasses

**Vulnerability:** SQL security filters in `ReadonlyDriver` and `SchemaValidationDriver` used simplistic regexes (e.g., `^\\s*`) that could be bypassed by leading SQL comments (e.g., `/* ... */ INSERT`). `ReadonlyDriver` also failed to detect DML operations nested within Common Table Expressions (CTEs), such as `WITH ... AS (INSERT ...)`.

**Learning:** Regex-based SQL parsing is fragile. Standard whitespace `\\s` does not account for SQL comments which databases treat as separators. Furthermore, SQL syntax allows data-modifying statements in unexpected places like CTEs in some dialects (e.g., PostgreSQL), which simple start-of-string keyword checks miss.

**Prevention:** Use a centralized, robust SQL pattern utility (`SqlPatterns`) that explicitly handles both whitespace and comments (`/*...*/` and `--...`) using `Pattern.DOTALL`. For read-only enforcement, specifically scan for DML keywords within `AS (...)` blocks of CTEs.
