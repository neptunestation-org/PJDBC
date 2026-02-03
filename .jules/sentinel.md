## 2026-02-03 - [DataMaskingDriver Bypass via Complex Types]
**Vulnerability:** The `DataMaskingDriver` failed to mask sensitive data when accessed through complex JDBC types such as `Blob`, `Clob`, `Array`, `Ref`, and specific `getObject` variants.
**Learning:** SQL proxy drivers must comprehensively override all possible data access methods in the `ResultSet` interface. Partially covering only common types (String, int, etc.) leaves a significant bypass surface for attackers or accidental data leaks.
**Prevention:** Use a "fail-closed" security posture for data masking. If a specific data type cannot be safely masked, the driver should throw an `SQLException` to prevent raw data exposure. Always review the full interface of proxy objects for potential leak points.
