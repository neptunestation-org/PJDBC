## 2026-04-06 - Data Masking Bypass via Column Aliasing
**Vulnerability:** DataMaskingDriver failed to mask columns when they were accessed via a label alias (e.g., `SELECT ssn AS alias`).
**Learning:** The driver was checking masking rules against the `columnLabel` provided in `getXXX(String columnLabel)` instead of resolving the label to the underlying column index. JDBC proxies should delegate label-based access to index-based access to ensure consistent transformation logic.
**Prevention:** Always use `findColumn(columnLabel)` to resolve indices in ResultSet proxies and centralize data transformation/masking logic in index-based getter methods.
