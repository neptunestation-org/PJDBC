# Sentinel Security Journal

## 2026-08-06 - Preventing NullPointerException in UserMapDriver Connect
**Vulnerability:** In `UserMapDriver.connect`, the `Properties info` parameter was used directly without checking if it was `null`. This could lead to an unhandled `NullPointerException` if a client called `Driver.connect` with `null` as the properties argument, leaking internal stack trace details or causing unexpected crashes.
**Learning:** Drivers should never assume that client-supplied parameters are non-null. When handling authentication or routing credentials, any unhandled runtime exceptions can expose internal details or lead to Denial of Service (DoS) situations.
**Prevention:** Always validate parameters such as `Properties` or configuration collections for non-nullness and validity, and throw secure, generic exceptions (like `SQLException` with generic messages) to hide implementation details.
