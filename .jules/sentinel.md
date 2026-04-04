## 2026-04-04 - [DataMaskingDriver Getter Coverage]
**Vulnerability:** Data leakage in DataMaskingDriver via unhandled ResultSet getter methods (LOBs, Arrays, and getObject variants).
**Learning:** AbstractResultSet only routes a subset of methods through transformValue. Security drivers must explicitly override all sensitive data accessors in the delegate interface to avoid bypasses.
**Prevention:** Use automated conformance tests to ensure all getter methods in a proxy driver are accounted for, either by transformation or by a 'fail-secure' exception.
