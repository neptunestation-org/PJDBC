## 2026-07-20 - NullPointerException prevention in UserMapDriver
**Vulnerability:** Passing a null Properties object to UserMapDriver.connect would cause an unhandled NullPointerException, which could leak internal stack traces or database details to callers depending on the application configuration.
**Learning:** Checking for null parameter objects is crucial at all system boundaries (e.g., driver entry points) to ensure the system fails securely with standard JDBC exceptions rather than unhandled RuntimeExceptions.
**Prevention:** Always validate all parameters of public entry points (like `connect` methods in JDBC drivers) for nullness before accessing any properties on them, and throw a secure, generic SQLException.
