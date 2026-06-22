# Sentinel's Journal - Critical Security Learnings

## 2026-06-22 - [Bypass Vulnerabilities in Proxy Drivers]
**Vulnerability:** Regex-based SQL filters in `ReadonlyDriver`, `SchemaValidationDriver`, and `WhereTransformer` were bypassable using SQL comments (e.g., `/* comment */ INSERT ...`) because they only checked for keywords at the absolute start of the string or with simple whitespace separators. `DataMaskingDriver` was bypassable via SQL aliases (e.g., `SELECT ssn AS public_id`) because it re-evaluated masking rules based on the potentially untrusted label rather than the underlying column index.

**Learning:** Simple regex anchors (`^`) and whitespace patterns (`\\s+`) are insufficient for SQL security filters as they ignore SQL's flexible comment syntax. Furthermore, security rules must be bound to immutable properties (like column indices) rather than mutable labels that can be manipulated in the query.

**Prevention:** Use a robust `PREFIX` or `SEP` regex that explicitly includes SQL comment patterns (`/*...*/` and `--...`) and `Pattern.DOTALL`. Always delegate label-based JDBC operations to index-based ones in proxy result sets to ensure consistent security policy application.
