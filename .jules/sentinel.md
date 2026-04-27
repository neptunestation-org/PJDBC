## 2026-04-27 - [DataMaskingDriver LOB and Typed getObject Bypass]
**Vulnerability:** Masked columns could be bypassed using complex type getters (getBlob, getClob, getArray, etc.) or the generic getObject(int, Class<T>) method, returning raw sensitive data instead of masked values.
**Learning:** AbstractResultSet only routes a subset of common getters through its transformValue hook. Security-focused drivers must explicitly override all potential data-access methods (especially LOBs and generic typed getters) to ensure consistent protection.
**Prevention:** Use a 'fail-secure' approach where unsupported data types in protected columns throw a SQLException rather than allowing raw access. Regularly audit proxy drivers against the full JDBC ResultSet interface for missing overrides.
