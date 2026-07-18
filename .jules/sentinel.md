# Sentinel's Journal - Critical Security Learnings Only

This journal contains CRITICAL security patterns and learnings specific to the PJDBC codebase.

## 2026-07-18 - Prevent NullPointerException leakage in Connection Driver Proxy
**Vulnerability:** In `UserMapDriver.java`, calling `connect(String, Properties)` with a `null` `Properties` info argument caused an unhandled `NullPointerException` (NPE) when trying to retrieve properties (e.g. `info.getProperty("user")`). Unhandled NPEs leak internal stack traces and implementation details to the client instead of failing securely.
**Learning:** Java JDBC Drivers can receive `null` or empty `Properties` configurations depending on client environment usage. Proxy drivers wrapping connection authentication details must proactively validate any method parameters before dereferencing them.
**Prevention:** Always perform a null-check on incoming `Properties` configurations inside proxy drivers before dereferencing key fields, throwing a controlled, generic `SQLException` when validation fails.
