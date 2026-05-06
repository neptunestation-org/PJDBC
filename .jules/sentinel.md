## 2026-05-06 - SQL Comment-Based Regex Bypass
**Vulnerability:** Security proxy drivers (`ReadonlyDriver`, `SchemaValidationDriver`) and SQL transformers (`SchemaTransformer`, `WhereTransformer`) could be bypassed by inserting SQL comments (`/*...*/` or `--...`) where the regex expected whitespace or at the start of the query.
**Learning:** Regex-based SQL filtering that uses `\s+` or `^\s*` is insufficient because databases ignore comments in those positions, but the regex fails to match them, leading to a "fail-open" state.
**Prevention:** Always use a robust separator pattern `(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+` and `Pattern.DOTALL` when parsing or filtering SQL with regex to ensure comments are treated as whitespace.

## 2026-05-06 - Unsafe Matcher.appendReplacement with SQL Comments
**Vulnerability:** Using `Matcher.appendReplacement` with raw SQL comments captured from the original query can cause crashes or SQL corruption if the comment contains special characters like `$` or `\`.
**Learning:** `appendReplacement` treats `$` and `\` as special characters for group references. If an attacker-controlled or even a legitimate comment contains these, it can trigger an `IllegalArgumentException` or `IndexOutOfBoundsException`.
**Prevention:** Always wrap replacement strings in `Matcher.quoteReplacement()` when they contain any part of the original input, especially comments or identifiers that might contain special characters.
