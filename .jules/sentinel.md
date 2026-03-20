# Sentinel's Journal - Critical Security Learnings

## 2026-03-20 - Data Masking Bypass via Complex JDBC Types and Typed Getters
**Vulnerability:** The `DataMaskingDriver` failed to mask several complex JDBC types (`Blob`, `Clob`, `Array`, `Ref`, `URL`, `RowId`, `SQLXML`) and the newer JDBC 4.2+ typed `getObject(column, Class<T>)` method, allowing full data leakage of "masked" columns.
**Learning:** Generic JDBC proxy frameworks that rely on a central `transformValue` hook often miss methods that don't return standard Objects or Strings. `AbstractResultSet` in this project only calls `transformValue` for a limited subset of getters.
**Prevention:** Security-focused `ResultSet` wrappers must explicitly override *every* getter method in the `ResultSet` interface to ensure a "fail-secure" posture, either by applying transformation/masking or by throwing an `SQLException` for unsupported types.
