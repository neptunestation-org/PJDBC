# Sentinel's Journal - Critical Security Learnings

## 2026-03-15 - Alias Bypass in Data Masking
**Vulnerability:** Sensitive columns could bypass masking if aliased (e.g., `SELECT secret AS public_name`) because the driver was checking the label against the masking configuration instead of the underlying column metadata. Additionally, LOB types (Blob, Clob) and complex JDBC types (Array, Ref) were not covered by the masking logic, allowing raw data leakage.
**Learning:** Label-based `ResultSet` getters are unreliable for security enforcement because SQL aliases can decouple the user-provided label from the configured sensitive column name. Furthermore, `AbstractResultSet` implementations often bypass transformation hooks for non-standard types.
**Prevention:** Always refactor label-based getters to resolve labels to indices via `findColumn()` and delegate to index-based counterparts that use pre-calculated security metadata. Maintain a "fail-secure" approach by explicitly overriding all `ResultSet` getter methods for sensitive columns, throwing `SQLException` if a safe masked representation cannot be provided.
