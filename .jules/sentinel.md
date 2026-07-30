# Sentinel Security Journal

## 2026-07-30 - DataMaskingDriver ResultSet Getter Bypass
**Vulnerability:** Unmasked data can be accessed via non-string getters or mapping methods (like `getBlob`, `getClob`, `getNClob`, `getSQLXML`, `getURL`, `getArray`, `getRef`, `getRowId`, or parameterized/typed `getObject` methods), bypassing data masking logic because only standard primitive and string getter methods were overridden.
**Learning:** Merely overriding the most common getter methods in ResultSet wrappers (like `getString`, `getInt`, etc.) is insufficient for secure data masking as JDBC provides numerous specialized, auxiliary, and typed getters that delegate directly to the underlying driver and leak the raw unmasked values.
**Prevention:** When implementing interceptor wrappers for JDBC objects (like ResultSet, Statement, Connection), always exhaustively audit and override all available delegating methods. Use a fail-closed strategy by throwing `SQLException` on restricted columns for any unsupported/specialized types.

## 2026-07-30 - OWASP Dependency-Check Transient NVD API Failures in CI
**Vulnerability:** Transient NVD API failures (503 Service Unavailable / 429 Too Many Requests) can fail CI builds completely if dependency-check-maven fails to download the NVD database, even when there are no dependency changes or issues.
**Learning:** Forcing SARIF report uploads unconditionally (`if: always()`) when dependency-check fails or is skipped causes additional GitHub Actions pipeline failures because the required `sarif_file` is not generated.
**Prevention:** In the CI workflow, set `continue-on-error: true` on the dependency-check execution step, cache the local database directory `~/.owasp`, and use a conditional check (e.g., `if [ -f target/... ]`) to only run the SARIF upload step if the report file was actually produced.
