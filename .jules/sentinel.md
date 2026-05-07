## 2026-05-07 - [LOB and Complex Type Bypasses in DataMaskingDriver]
**Vulnerability:** Masking was bypassed when data was retrieved via specialized JDBC methods like `getBlob()`, `getClob()`, `getArray()`, `getURL()`, or `getObject(int, Class<T>)`.
**Learning:** Security proxies must ensure total coverage of the delegated interface. Any method returning sensitive data that is not explicitly handled is a potential bypass.
**Prevention:** Use a "fail-secure" approach by overriding all data retrieval methods to throw an exception on protected resources if a specific transformation (like masking) cannot be safely applied.
