# Sentinel's Journal - Critical Security Learnings

## 2026-03-27 - SQL Comment Bypass in Proxy Drivers
**Vulnerability:** Security proxy drivers (Readonly, SchemaValidation) and SQL transformers used regex patterns that relied on whitespace (\s+) to separate SQL keywords. Attackers could bypass these checks by using SQL comments (/* comment */ or -- line comment) as separators, which are valid in most SQL dialects but not matched by \s+.
**Learning:** Standard whitespace regexes are insufficient for SQL parsing when security boundaries are involved. SQL comments must be treated as valid separators between tokens.
**Prevention:** Centralized SQL separator regexes in a utility class (SqlPatterns) that explicitly include block and line comments. Use these patterns in all security-critical regexes. When using Pattern.DOTALL, ensure line comment patterns ([^\r\n]*) do not greedily consume the rest of the query.
