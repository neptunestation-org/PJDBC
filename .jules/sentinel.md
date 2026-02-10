# Sentinel Journal - Security Learnings

## 2026-02-10 - Data Leakage via Uncovered JDBC Types in Proxy Drivers
**Vulnerability:** Proxy drivers (like `DataMaskingDriver`) that intercept database results for security purposes (masking, filtering, etc.) often only override common getters (e.g., `getString`, `getInt`). Complex types like `Blob`, `Clob`, `Array`, `RowId`, and `SQLXML` were left uncovered, allowing sensitive data to be retrieved in its original form if accessed via these specific getters.
**Learning:** Completeness in JDBC proxy implementation is critical. Any method in the `ResultSet` interface that returns data is a potential bypass vector.
**Prevention:** When implementing security proxies for JDBC, perform a systematic audit of all `ResultSet` (and `CallableStatement` out-parameters) getter methods. For any data type that cannot be safely transformed or masked, the proxy should "fail secure" by throwing a `SQLException` rather than allowing a pass-through to the original data.
