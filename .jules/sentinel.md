
## 2026-02-06 - [SchemaValidationDriver Bypass]
**Vulnerability:** Regex-based SQL validation for tables and columns was bypassed using quoted identifiers (e.g., "table") and comma-separated lists in the FROM clause.
**Learning:** Simple word-boundary and alphanumeric regexes are insufficient for SQL parsing because SQL supports various quoting styles (quotes, backticks, brackets) and complex clause structures.
**Prevention:** Always support standard SQL quoting styles in identifier regexes and implement an unquote/normalization step before validation. For list-based clauses like FROM, explicitly handle comma-separated tokens.
