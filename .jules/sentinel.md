# Sentinel's Journal - Critical Security Learnings

## 2026-03-28 - Comment-based Bypasses of Regex SQL Parsers
**Vulnerability:** SQL comments (e.g., `/* ... */` or `-- ...`) can be used as separators between SQL keywords and identifiers, bypassing simple regex patterns that rely on `\s+` or `^\\s*`.
**Learning:** Security-focused regexes in this repository must account for comments as valid separators. Standard whitespace matches are insufficient. Centralizing these patterns in a `SqlPatterns` utility ensures consistent hardening across drivers.
**Prevention:** Use `SqlPatterns.SQL_SEP` (mandatory) or `SqlPatterns.SQL_SEP_OPT` (optional) in all security-critical SQL regexes.

## 2026-03-28 - Special Characters in Matcher.appendReplacement
**Vulnerability:** Using `Matcher.appendReplacement` with captured strings (like separators) can cause `IndexOutOfBoundsException` or incorrect output if those strings contain special characters like `$` or `\`.
**Learning:** Captured SQL components, especially comments, may contain these characters.
**Prevention:** Always wrap the replacement string in `Matcher.quoteReplacement()` when it contains data from the source SQL.
