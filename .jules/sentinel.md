## 2026-04-19 - DataMaskingDriver Bypass via LOB and Complex Types
**Vulnerability:** Sensitive data in masked columns could be retrieved unmasked using LOB-specific getters (`getBlob`, `getClob`, etc.) and the generic `getObject(..., Class<T>)` overloads.
**Learning:** Security-focused `ResultSet` wrappers must comprehensively override all data retrieval methods in the `ResultSet` interface. Any method not explicitly handled that delegates directly to the underlying `ResultSet` is a potential security bypass.
**Prevention:** Use a 'fail-secure' approach by default: any method that retrieves data should either apply masking or throw a `SQLException` if it's not feasible to mask that specific data type.
