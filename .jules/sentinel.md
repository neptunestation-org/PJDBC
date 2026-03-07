## 2026-03-07 - SQL Comment Regex Bypass
**Vulnerability:** Security-critical SQL filters (ReadonlyDriver, SchemaValidationDriver) and transformers (WhereTransformer, SchemaTransformer) used simple whitespace regexes (\s) for parsing, which could be bypassed by using SQL comments (/*...*/ or --...) as separators.
**Learning:** In SQL, comments are valid separators anywhere whitespace is allowed. Regexes that only account for \s+ or \s* are insufficient for security boundaries in SQL proxies.
**Prevention:** Use a centralized, comment-aware SQL separator regex (SqlPatterns.SQL_SEP) that explicitly matches whitespace, block comments, and line comments. Always anchor start-of-statement detection regexes with ^ to avoid false positives on string literals.
