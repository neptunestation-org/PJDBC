## 2026-05-05 - SQL Comment-based Regex Bypass
**Vulnerability:** Security filters (Readonly, Schema Validation) and Transformers (Where, Schema) using `\s*` or `\s+` could be bypassed by using SQL comments (`/*...*/` or `--...`) instead of whitespace to hide DML/DDL keywords or table names.
**Learning:** Many JDBC drivers and databases treat comments as whitespace or ignore them before the statement. Regexes anchored at `^` or looking for space after keywords fail to match if a comment is present.
**Prevention:** Use a robust whitespace/comment pattern like `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*` and enable `Pattern.DOTALL` to ensure security filters and transformers correctly identify the statement type and identifiers even when preceded or separated by complex comment blocks.
