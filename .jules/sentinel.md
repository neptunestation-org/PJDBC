## 2026-02-02 - [Fix data leak in DataMaskingDriver]
**Vulnerability:** The `DataMaskingDriver` failed to mask complex JDBC types like `Blob`, `Clob`, and `Array`, allowing sensitive data to be retrieved via their respective getter methods even when the column was configured for masking.
**Learning:** In a proxy JDBC driver, every possible way to access data must be intercepted. Overriding only `getString` or `getObject` is insufficient if the underlying driver supports more specialized getters.
**Prevention:** Use a "fail-secure" approach by overriding all `ResultSet` getter methods that are not explicitly handled with masking logic to throw a `SQLException`.
