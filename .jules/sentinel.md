
## 2026-04-02 - SQL Comment Bypass in Regex Parsing
**Vulnerability:** Security drivers (ReadonlyDriver, SchemaValidationDriver) and SQL transformers (WhereTransformer, SchemaTransformer) used simple whitespace regexes (\s+ or \s*) to identify SQL keywords and boundaries. This allowed bypassing security checks by using SQL comments (/* comment */ or -- line comment) instead of whitespace.
**Learning:** Databases treat SQL comments as valid separators between tokens. Simple whitespace-based regexes fail to account for this, creating a significant bypass vector in proxy-based security controls.
**Prevention:** Centralize SQL separator patterns in a utility class (SqlPatterns) that includes both whitespace and SQL comment syntax. Use these robust patterns (SQL_SEP and SQL_SEP_OPT) in all security-critical regexes.
