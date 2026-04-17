## 2026-04-17 - [Data Masking Bypass via Complex JDBC Types]
**Vulnerability:** DataMaskingDriver failed to intercept complex JDBC type getters (getBlob, getClob, getArray, getURL, etc.) on masked columns, allowing unmasked sensitive data to be retrieved.
**Learning:** The JDBC ResultSet interface is extensive. Overriding only common getters (getString, getInt) is insufficient for security proxies; an exhaustive override of all data-retrieval methods is required to prevent bypasses.
**Prevention:** When implementing security-focused JDBC wrappers, use a "deny-by-default" strategy or comprehensively override all delegated methods that return data.
