## 2026-02-07 - DataMaskingDriver Complex Type Bypass
**Vulnerability:** The `DataMaskingDriver` failed to override complex JDBC type getters (Blob, Clob, Array, Ref, etc.) in its proxy `ResultSet`. This allowed users to bypass data masking by retrieving sensitive data through these methods instead of `getString()`.
**Learning:** Proxying a complex interface like `ResultSet` requires exhaustive coverage of all data retrieval methods. Any method left to the default delegation can become a security bypass.
**Prevention:** Use a "fail-secure" approach by explicitly overriding all methods that could return sensitive data. If masking is not applicable to a specific type, the method should throw an `SQLException` for masked columns, forcing the use of safe alternatives like `getString()`.

## 2026-02-07 - Overly Strict Conformance Tests for Parameter Names
**Vulnerability:** Not a vulnerability per se, but a CI-breaking issue. Conformance tests enforced Java-style identifier naming on URL parameters, but some drivers (like `FilterDriver`) use wildcards like `rename.*` which are valid and necessary for their functionality.
**Learning:** Generic conformance tests must account for advanced usage patterns like wildcards in dynamic parameters.
**Prevention:** Update test regexes to support valid characters like `.` and `*` when they are part of the supported parameter naming scheme.
