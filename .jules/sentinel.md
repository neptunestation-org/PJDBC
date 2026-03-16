# Sentinel Security Journal

## 2026-03-16 - DataMaskingDriver Alias and LOB Bypass
**Vulnerability:** The `DataMaskingDriver` could be bypassed in two ways:
1. **Column Aliasing:** By renaming a sensitive column in the SQL query (e.g., `SELECT ssn AS public_info`), the driver's label-based masking check failed because it matched the alias `public_info` against the configuration, rather than the underlying column `ssn`.
2. **Missing Method Coverage:** Many `ResultSet` getter methods (e.g., `getBlob`, `getClob`, `getArray`, `getObject(..., Class)`) were not overridden, allowing sensitive data to be retrieved unmasked if the appropriate type-specific getter was used.

**Learning:**
- In JDBC proxy drivers, label-based lookups are inherently risky because they rely on user-controlled aliases.
- High-security `ResultSet` wrappers must delegate all label-based methods to index-based methods after resolving the index via `findColumn()`.
- Comprehensive interface coverage is essential; any missing method in a security proxy is a potential leak.

**Prevention:**
- Always resolve labels to indices at the earliest possible stage in proxy logic.
- Use a "fail-secure" approach by overriding all sensitive methods in the interface to throw an exception if transformation/masking isn't possible, rather than allowing the default passthrough.
