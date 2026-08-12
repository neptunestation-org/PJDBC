# Sentinel Security Journal

## 2026-08-12 - UserMapDriver Null Properties Information Disclosure
**Vulnerability:** Unhandled NullPointerException when the properties parameter was null. This leaked internal stack trace details, violating secure coding practices.
**Learning:** The driver assumed that a `Properties` object is always provided by callers, overlooking the JDBC spec where callers might invoke `connect(url, null)` under some conditions, resulting in an unhandled exception before authentication validation.
**Prevention:** Always validate all parameters of public driver entry points (`connect`) for nullability and throw custom, generic, secure `SQLException`s rather than allowing standard runtime exceptions to propagate.
