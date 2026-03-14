## 2026-03-14 - DataMaskingDriver Security Enhancement
**Vulnerability:** DataMaskingDriver had multiple security leaks:
1. Aliasing bypass: `SELECT secret AS alias` bypassed masking if `alias` wasn't in the mask list, because label-based getters checked the label instead of the underlying column index.
2. LOB/Complex type leakage: `getBlob`, `getClob`, and other complex types were not overridden, allowing raw sensitive data to be retrieved.
3. `getObject` variants: Some `getObject` signatures were not properly handled, allowing bypasses.

**Learning:**
1. Label-based getters in JDBC proxies should almost always resolve to index-based getters via `findColumn(label)` to ensure consistent policy application regardless of SQL aliasing.
2. Comprehensive interface coverage is critical for security proxies. Any method not explicitly handled in a security `ResultSet` wrapper that returns data is a potential bypass.
3. PJDBC's `AbstractResultSet` only calls its `transformValue` hook for a limited subset of getter methods; others bypass it, requiring explicit overrides in security drivers.

**Prevention:**
1. Always delegate label-based methods to index-based methods in JDBC proxy implementations.
2. Use a 'fail-secure' approach by throwing `SQLException` for any data type that cannot be safely transformed or masked.
3. Maintain a security test suite that specifically attempts bypasses via aliasing and various data access methods.
