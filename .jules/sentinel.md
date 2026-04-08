
## 2026-04-08 - Regex-based SQL security bypass via comments
**Vulnerability:** ReadonlyDriver, SchemaValidationDriver, and SchemaTransformer used regex patterns that assumed whitespace (\s) as the only separator. This allowed attackers to bypass security checks by using SQL comments (/*...*/ or --...) which the regex did not recognize as separators.
**Learning:** In SQL, comments can often replace whitespace between keywords and identifiers. Regex-based security filters must explicitly account for all forms of SQL comments to prevent bypasses.
**Prevention:** Use a robust separator pattern like (?:\s|/\*.*?\*/|--.*?(?:\n|$))+ and ensure Pattern.DOTALL is used to handle multiline comments. When performing replacements, use Matcher.quoteReplacement() if the matched separator is included in the replacement string.
