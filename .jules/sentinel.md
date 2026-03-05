## 2026-03-05 - SQL Comment Bypass in Security Filters
**Vulnerability:** Security filters (readonly, schema validation) and SQL transformers that rely on `\s+` or `\b` for keyword separation can be bypassed by using SQL comments (`/**/` or `--`) as separators.
**Learning:** SQL parsers treat comments as whitespace, but standard regex `\s` does not match them. Attackers can evade simple regex-based security checks by replacing mandatory spaces with comments.
**Prevention:** Use a centralized, robust regex pattern for SQL separators that explicitly includes block comments and line comments. Always capture and preserve these separators when performing SQL transformations to maintain formatting and avoid breaking queries.
