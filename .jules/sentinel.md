## 2026-02-07 - DataMaskingDriver Complex Type Bypass
**Vulnerability:** The `DataMaskingDriver` failed to override complex JDBC type getters (Blob, Clob, Array, Ref, etc.) in its proxy `ResultSet`. This allowed users to bypass data masking by retrieving sensitive data through these methods instead of `getString()`.
**Learning:** Proxying a complex interface like `ResultSet` requires exhaustive coverage of all data retrieval methods. Any method left to the default delegation can become a security bypass.
**Prevention:** Use a "fail-secure" approach by explicitly overriding all methods that could return sensitive data. If masking is not applicable to a specific type, the method should throw an `SQLException` for masked columns, forcing the use of safe alternatives like `getString()`.
