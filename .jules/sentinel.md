# Sentinel Security Journal

## 2026-02-21 - Label-based Bypass in JDBC Proxy Drivers
**Vulnerability:** Proxy drivers that wrap `ResultSet` and perform security checks (like masking or validation) in label-based getters (e.g., `getString(String label)`) are vulnerable to bypasses via column aliasing. If the config only lists the original column name, but the query uses an alias, the label-based getter might not recognize the column as sensitive.
**Learning:** Checking both `meta.getColumnName(i)` and `meta.getColumnLabel(i)` at initialization is necessary but not sufficient if label-based getters only check the provided label against the config.
**Prevention:** Always resolve labels to indices using `findColumn(label)` and delegate to index-based getters. This ensures that the security check is applied to the underlying column regardless of how it was referenced in the getter call.

## 2026-02-21 - LOB and Complex Type Leakage in Data Masking
**Vulnerability:** Data masking drivers that only override common getters (like `getString`, `getInt`) leak sensitive data if the user calls getters for complex types like `getBlob`, `getClob`, `getArray`, etc.
**Learning:** Total coverage of the `ResultSet` interface is required for a secure proxy. Any method not handled represents a potential bypass.
**Prevention:** For data masking, "fail-secure" by overriding ALL getter methods for sensitive columns. If a type cannot be masked, it should throw an `SQLException` rather than leaking raw data.
