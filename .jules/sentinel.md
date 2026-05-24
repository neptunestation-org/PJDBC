## 2026-05-24 - SQL Comment-Based Security Bypass
**Vulnerability:** Regex-based SQL security filters (ReadonlyDriver, SchemaValidationDriver, FederatingDriver) could be bypassed by using SQL comments (`/*...*/` or `--...`) as a substitute for whitespace or as a prefix to the entire statement.
**Learning:** Standard regexes starting with `^\s*` do not account for leading comments, and `\s+` does not account for comments as delimiters between SQL keywords.
**Prevention:** Use a robust `PREFIX` pattern (`^(?:\s|/\*.*?\*/|--.*?(?:\n|$))*`) and `SEP` pattern (`(?:\s|/\*.*?\*/|--.*?(?:\n|$))+`) combined with `Pattern.DOTALL` for all security-critical SQL parsing.
