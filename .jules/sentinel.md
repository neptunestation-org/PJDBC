## 2026-02-12 - [Data Masking Bypass for Complex Types]
**Vulnerability:** The `DataMaskingDriver` failed to mask data when retrieved through LOB getters (`getBlob`, `getClob`, etc.) and specific `getObject(..., Class<T>)` overloads.
**Learning:** In JDBC proxy drivers, simply overriding `getString` and `getObject(int/String)` is insufficient if the underlying database supports complex types. Any getter method in `ResultSet` that is not overridden will delegate to the original driver, potentially leaking sensitive data.
**Prevention:** Always implement a "fail-secure" default or explicitly override all data-retrieval methods in sensitive proxy drivers. For types that cannot be easily masked, throwing an `SQLException` is safer than allowing a bypass.
