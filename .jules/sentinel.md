# Sentinel Security Journal

## 2026-02-09 - [SchemaValidationDriver Bypass via Quoted Identifiers]
**Vulnerability:** The `SchemaValidationDriver` regex-based SQL parsing was vulnerable to bypasses using quoted identifiers (double quotes, backticks, square brackets). This allowed attackers to access blocked tables or columns by simply quoting their names in SQL statements.
**Learning:** Naive regex for SQL identifier extraction (e.g. `[a-zA-Z_][a-zA-Z0-9_]*`) is insufficient when databases support various quoting styles and spaces in names. Furthermore, splitting by space for alias detection can incorrectly split quoted identifiers containing spaces.
**Prevention:** Use a comprehensive `IDENTIFIER_REGEX` that covers all supported quoting styles and handle unescaping. When extracting identifiers from expressions, check if the expression matches a single identifier before attempting to split for aliases, and use `while(matcher.find())` to capture all identifiers within complex expressions like aggregate functions.
