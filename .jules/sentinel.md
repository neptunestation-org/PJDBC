# Sentinel Security Journal - Critical Learnings Only

## 2026-07-24 - Data Masking Bypass via Auxiliary ResultSet Getters
**Vulnerability:** The DataMaskingDriver's proxy ResultSet only masked values returned from standard getters (such as getString, getInt, and binary streams). It did not override auxiliary or specialized getters like getBlob(), getClob(), getNClob(), getSQLXML(), getURL(), getArray(), getRef(), getRowId(), or the typed/mapped getObject() overloads, allowing callers to completely bypass masking controls on matched columns.
**Learning:** Proxy/wrapper patterns for security controls (like data masking) must comprehensively cover all interface operations. Partial coverage of interface getters leaves open significant bypass vectors that delegate directly to the underlying resource.
**Prevention:** Always audit the full interface definition (e.g., java.sql.ResultSet) when designing wrapper-based security filters and block or mask all alternate entry points to ensure complete defense in depth.
