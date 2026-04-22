## 2026-04-22 - DataMaskingDriver bypass via LOB and typed getObject
**Vulnerability:** Sensitive data can be retrieved from masked columns using `getBlob`, `getClob`, or `getObject(index, Class<T>)` because these methods were not overridden in the proxy `ResultSet`.
**Learning:** The `AbstractResultSet` base class only routes a subset of methods through its transformation hook. Security drivers must perform a "total coverage" audit of the interface they are proxying, as any unhandled method that returns data is a potential bypass.
**Prevention:** Use a "fail-secure" approach where all data-returning methods in a security proxy are either masked or explicitly blocked (throwing `SQLException`) if they cannot be safely masked.
