# Sentinel Security Journal

## 2026-07-30 - DataMaskingDriver ResultSet Getter Bypass
**Vulnerability:** Unmasked data can be accessed via non-string getters or mapping methods (like `getBlob`, `getClob`, `getNClob`, `getSQLXML`, `getURL`, `getArray`, `getRef`, `getRowId`, or parameterized/typed `getObject` methods), bypassing data masking logic because only standard primitive and string getter methods were overridden.
**Learning:** Merely overriding the most common getter methods in ResultSet wrappers (like `getString`, `getInt`, etc.) is insufficient for secure data masking as JDBC provides numerous specialized, auxiliary, and typed getters that delegate directly to the underlying driver and leak the raw unmasked values.
**Prevention:** When implementing interceptor wrappers for JDBC objects (like ResultSet, Statement, Connection), always exhaustively audit and override all available delegating methods. Use a fail-closed strategy by throwing `SQLException` on restricted columns for any unsupported/specialized types.
