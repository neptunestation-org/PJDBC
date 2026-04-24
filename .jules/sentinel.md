# Sentinel's Journal - Critical Security Learnings

## 2026-04-24 - DataMaskingDriver Complex Type Bypass
**Vulnerability:** DataMaskingDriver failed to mask columns when accessed via complex JDBC types like `getBlob()`, `getClob()`, `getArray()`, etc.
**Learning:** `AbstractResultSet` only provides hooks for a limited set of common getters. Proxies intended for security or data filtering must explicitly override all potential data accessors in the `ResultSet` interface to prevent bypasses.
**Prevention:** When implementing security-focused `ResultSet` wrappers, ensure total coverage of the delegated interface. Any method not handled that returns sensitive data is a potential bypass. Throwing `SQLException` is a safe 'fail-secure' approach for types that cannot be easily masked.
