## 2026-02-13 - [Proxy Interface Coverage for Security]
**Vulnerability:** Data masking logic in `DataMaskingDriver` was bypassed by several `ResultSet` methods (LOBs, URLs, Arrays, etc.) because they were not explicitly overridden in the proxy class.
**Learning:** When developing security-focused proxy drivers, it is critical to ensure total coverage of the delegated interface. Any method not explicitly handled that returns sensitive data represents a potential bypass.
**Prevention:** Use a "fail-secure" pattern where all methods in the proxy interface are reviewed for sensitive data access, and any that cannot be safely transformed or masked should throw an exception.
