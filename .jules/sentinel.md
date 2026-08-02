## 2026-08-02 - Null Connection Properties Validation in UserMapDriver
**Vulnerability:** Calling connect on UserMapDriver with a null Properties object results in a NullPointerException, which propagates as an unhandled exception and could leak internal driver details.
**Learning:** General JDBC Driver connect contracts allow null properties in some containers, and failing to guard against null results in a NullPointerException.
**Prevention:** Always validate connection properties (`info` parameter) to be non-null in proxy drivers, throwing a secure, generic SQLException.
