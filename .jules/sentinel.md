## 2026-04-09 - Comment-Based Security Filter Bypass
**Vulnerability:** SQL security filters (ReadonlyDriver, SchemaValidationDriver) and transformers (SchemaTransformer) could be bypassed by using SQL comments (/*...*/ or --...) instead of whitespace.
**Learning:** Regex patterns like `^\\s*(INSERT|UPDATE|...)` are insufficient because SQL parsers treat comments as whitespace delimiters, but `\\s` does not match them.
**Prevention:** Use a more robust whitespace pattern that explicitly includes comments: `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+`. Also ensure `Pattern.DOTALL` is enabled to handle multi-line comments.
