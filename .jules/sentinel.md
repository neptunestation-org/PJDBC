# Sentinel's Journal - Critical Security Learnings

## 2026-03-18 - LOB and Complex Type Leakage in DataMaskingDriver
**Vulnerability:** The `DataMaskingDriver` failed to mask columns when accessed via LOB-specific getters (e.g., `getBlob`, `getClob`) and other complex type accessors (e.g., `getArray`, `getSQLXML`), leaking the original sensitive data.
**Learning:** Proxy-based security drivers must explicitly override *all* data retrieval methods of the delegated interface. Standardizing on `getString` or `getObject` is insufficient if the interface provides specialized accessors that bypass the common transformation hooks.
**Prevention:** In JDBC proxy drivers, ensure total coverage of `ResultSet` getter methods. Use a fail-secure approach (throwing `SQLException`) for types that cannot be safely transformed or masked, and use standard parameter names (e.g., `i` for index, `s` for label) to keep security patches concise and within line limits.
