## 2026-02-04 - [Blocking complex types in DataMaskingDriver]
**Vulnerability:** Data leakage via complex JDBC types (Blob, Clob, Array, etc.) in a masking driver.
**Learning:** A JDBC proxy driver that implements data masking must override *all* possible getter methods in `ResultSet`. If a specific type cannot be masked, it must throw an `SQLException` instead of delegating to the underlying driver, which would return unmasked data.
**Prevention:** Always perform a full audit of all interface methods when implementing a security-related proxy. Use a dedicated security test suite that attempts to bypass the security control using various data types and methods.
