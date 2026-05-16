## 2026-05-16 - SQL Comment-Based Security Bypass
**Vulnerability:** Drivers and transformers using regex-based SQL parsing were vulnerable to bypasses where SQL comments (`/*...*/` or `--...`) were used instead of whitespace to delimit keywords. For example, `/* comment */ INSERT` bypassed `^\\s*INSERT`.
**Learning:** Standard `\\s` in Java regex does not match SQL comments. Many PJDBC drivers relied on `^\\s*` or `\\s+` to identify SQL statement types and table/column names, which could be subverted.
**Prevention:** Use a robust whitespace and comment pattern like `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*` and ensure `Pattern.DOTALL` is used for multi-line comments. Predefined `PREFIX` and `SEP` constants in drivers can help maintain consistency.
