## 2026-03-10 - [DataMaskingDriver Bypass via Aliasing and LOBs]
**Vulnerability:** Masking could be bypassed by aliasing columns (e.g., `SELECT secret AS safe`) or using non-string getters like `getBlob`, `getObject(..., Class)`, or `getByte`.
**Learning:** `ResultSet` wrappers must delegate label-based getters to index-based ones to handle aliases, and must provide exhaustive coverage of all data-returning methods, failing securely for unsupported types.
**Prevention:** Use a centralized masking check based on column index and ensure all `ResultSet` getter methods are overridden in the proxy.
