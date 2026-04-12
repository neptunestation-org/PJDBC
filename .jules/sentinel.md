## 2026-04-12 - Regex Bypass via SQL Comments
**Vulnerability:** Regex-based SQL security filters (ReadonlyDriver, SchemaValidationDriver) could be bypassed by prefixing statements with SQL comments (/*...*/ or --...) or using them as delimiters where whitespace was expected.
**Learning:** Standard regex anchors like ^ and whitespace \s are insufficient when SQL allows comments almost anywhere. Lookahead assertions are necessary in complex extractions to prevent greedy matches from consuming subsequent keywords (e.g., JOIN).
**Prevention:** Always use a robust whitespace-or-comment pattern (?:\s|/\*.*?\*/|--.*?(?:\n|$))+ and Pattern.DOTALL. For identifier extraction, use non-greedy matches combined with lookahead for SQL reserved words.
