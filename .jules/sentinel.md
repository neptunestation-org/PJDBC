## 2026-06-11 - Fixed pre-existing ParameterDefaultConformanceTest failure
**Vulnerability:** Not a vulnerability, but a CI-blocking test failure. `FilterDriver` uses `rename.*` as a parameter name, which was being rejected by a conformance test that expected only valid Java identifiers.
**Learning:** General-purpose conformance tests must sometimes account for wildcard or domain-specific parameter naming schemes used by specific components.
**Prevention:** Updated the validation regex in `ParameterDefaultConformanceTest` to allow the `.*` suffix for parameters.
