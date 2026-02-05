## 2026-02-05 - SQL Identifier Bypass in Security Drivers
**Vulnerability:** Regex-based SQL parsing in `SchemaValidationDriver` and `SchemaTransformer` failed to identify quoted identifiers (double quotes, backticks, brackets), allowing bypass of security whitelists/blacklists and schema-prefixing logic.
**Learning:** Naive regex like `[a-zA-Z_][a-zA-Z0-9_]*` is insufficient for SQL identifiers which can be quoted and contain special characters (including dots).
**Prevention:** Use a robust identifier regex that supports all quoting styles and handle potential schema qualification by using capture groups for optional parts. Centralize this logic in a base class or utility.
