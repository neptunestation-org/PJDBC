## 2026-06-21 - Hardening DataMaskingDriver
**Vulnerability:** DataMaskingDriver had multiple security bypasses:
1. LOB types (Blob, Clob, NClob) were not covered by masking logic, allowing full data exposure.
2. The `getObject(label, Class)` and `getObject(index, Class)` methods were missing, allowing bypass via type-specific requests.
3. Label-based getters (e.g., `getString(String columnLabel)`) only checked the label against masking patterns, allowing bypass via SQL aliasing (e.g., `SELECT ssn AS public_id`).

**Learning:** JDBC drivers have a vast surface area of data retrieval methods. Security proxies must explicitly override not just common methods (like `getString`), but also specialized ones (`getObject`, LOBs, etc.) to ensure complete coverage. Label-based methods are particularly dangerous if they don't delegate to index-based resolution, as aliases can decouple the label from the underlying column name.

**Prevention:**
1. Always delegate label-based JDBC method overrides to their index-based counterparts using `findColumn(label)`.
2. Exhaustively override all data-returning methods in `ResultSet` wrappers.
3. For `DataMaskingDriver`, throw `SQLException` for non-string types on masked columns to fail securely rather than returning potentially confusing default values.
