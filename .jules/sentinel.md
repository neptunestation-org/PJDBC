# Sentinel's Journal - Critical Security Learnings

## 2026-03-31 - DataMaskingDriver Aliasing and LOB Bypass
**Vulnerability:** DataMaskingDriver could be bypassed by aliasing columns (e.g., `SELECT ssn AS alias`) because masking was checked against the label only. Additionally, LOB types (`Blob`, `Clob`) and complex types (`Array`, `URL`) were not covered, allowing access to raw sensitive data.
**Learning:** `ResultSet.getMetaData()` provides both `columnName` and `columnLabel`. Pre-calculating a `boolean[]` of masked column indices at `ResultSet` initialization is more secure and efficient than string-based lookups during retrieval.
**Prevention:** In JDBC proxy drivers, always prefer index-based retrieval for internal logic. Delegate all label-based getter methods to their index-based counterparts via `findColumn(label)` to ensure consistent security policy application regardless of SQL aliasing.

## 2026-03-31 - Wildcard Parameter Validation
**Vulnerability:** Conformance tests for `@DriverParameter` were too strict, rejecting valid wildcard patterns like `rename.*` used in `FilterDriver`.
**Learning:** Security drivers often need flexible parameter naming for dynamic configuration.
**Prevention:** Update conformance regexes to support common extension patterns: `[a-zA-Z][a-zA-Z0-9_]*(\\.\\*)?`.
