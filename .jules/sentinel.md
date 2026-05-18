## 2026-05-18 - [DataMaskingDriver] Missing ResultSet Overrides
**Vulnerability:** Several `ResultSet` getter methods (e.g., `getBlob`, `getClob`, `getObject(int, Class)`) were not overridden in `MaskingResultSet`, allowing raw sensitive data to be retrieved from columns that should be masked.
**Learning:** `AbstractResultSet` only delegates a subset of methods to the `transformValue` hook. Complex types and newer JDBC methods bypass this hook entirely.
**Prevention:** When implementing a security proxy for `ResultSet`, ensure exhaustive coverage of all getter methods. Use a "fail-secure" approach by throwing `SQLException` for types that cannot be safely masked.
