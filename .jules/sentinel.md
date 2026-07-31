## 2026-07-31 - [NPE and Information Leak via Null Connection Properties]
**Vulnerability:** Drivers that process the `Properties info` parameter in their `connect` method can throw an unhandled `NullPointerException` if the client passes a `null` properties object. This can leak implementation details and internal stack traces.
**Learning:** Checking for user/password keys directly on `Properties info` without checking if `info` itself is null causes unexpected runtime exceptions.
**Prevention:** Always perform a null check on the `Properties info` parameter at the start of `connect` before accessing any properties, and fail securely with a generic `SQLException("PJDBC: Authentication failed")` or similar standard exception.
