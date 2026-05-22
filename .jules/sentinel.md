## 2026-05-22 - SQL Comment Bypass in Security Drivers
**Vulnerability:** Security-focused JDBC proxy drivers (`ReadonlyDriver`, `SchemaValidationDriver`, `FederatingDriver`) and SQL transformers (`SchemaTransformer`, `WhereTransformer`) used simple whitespace-based regexes (`\\s+` or `^\\s*`) to identify SQL keywords and identifiers. This allowed malicious or accidental bypasses using SQL comments (`/*...*/` and `--...`) instead of whitespace (e.g., `/* comment */ INSERT INTO ...`).

**Learning:** Regex-based SQL security controls are extremely fragile. SQL comments are often ignored by simple parsers but are valid statement delimiters in most databases. Standardizing on a robust 'separator' pattern and always using `Pattern.DOTALL` for multi-line support is critical for defense-in-depth components.

**Prevention:** Use a reusable regex pattern for SQL separators: `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+`. Ensure all security-sensitive SQL extraction logic supports this pattern and correctly handles multi-line input.
