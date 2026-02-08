## 2026-02-08 - DataMaskingDriver Complex Type Bypass
**Vulnerability:** DataMaskingDriver failed to mask sensitive data when accessed via complex JDBC types like Blobs, Clobs, and Arrays, because its proxy ResultSet only overrode a subset of getter methods.
**Learning:** Security proxy drivers must provide total coverage of the delegated interface to prevent bypasses. Any method returning data that is not explicitly handled will delegate to the original, unmasked source.
**Prevention:** Use a 'fail-secure' approach by ensuring all data retrieval methods in proxy drivers are accounted for. In this project's architecture, throwing an SQLException for non-transformable types is the preferred way to signal that a column is protected.
