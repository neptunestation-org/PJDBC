## 2026-05-08 - SQL Comment and Aliasing Bypasses in Security Drivers
**Vulnerability:** SQL security drivers (ReadonlyDriver, SchemaValidationDriver) using simple whitespace-based regex patterns were bypassed by prefixing or interspersing keywords with SQL comments (/*...*/ or --...). DataMaskingDriver was bypassed by using column aliases (AS alias) because label-based getters checked the alias against the masking patterns instead of the underlying column.

**Learning:** Regex-based SQL filtering is fragile and must explicitly account for all forms of whitespace and separators allowed by the SQL dialect, including multiple types of comments. Security proxies for ResultSets must ensure that all access paths (index vs. label) lead to the same security checks, preferably by canonicalizing to index-based access.

**Prevention:** 1. Use a robust separator pattern in SQL regexes: `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+`. 2. Use `Pattern.DOTALL` when matching comments. 3. Refactor ResultSet wrappers to resolve column labels to indices early and perform all security checks based on those indices.
