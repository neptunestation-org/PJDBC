# PRD: Agent-First Distribution for PJDBC (Open-Source Ecosystem)

Owner: PJDBC maintainers
Status: Approved
Last updated: 2026-01-01  
Target project: PJDBC (Java JDBC proxy library)

## 1. Summary

As AI agents write more software, open-source dependencies will increasingly be selected, integrated, and updated automatically. PJDBC is a developer-facing library (not an end-user application) and is likely to be consumed directly by agents in build pipelines. This PRD defines an actionable plan to make PJDBC “agent-first” in open-source ecosystems by:

1) Increasing trust and verifiability of releases (supply-chain assurance).  
2) Making PJDBC capabilities machine-discoverable (capability manifests + introspection).  
3) Publishing conformance tests so integrations are predictable and safe.

This work should improve reliability for both human developers and automated agents, while remaining compatible with standard Java OSS distribution channels.

## 2. Problem Statement

Today, most open-source distribution channels are optimized for humans:
- Humans read READMEs to learn usage patterns.
- Humans infer safety and compatibility from conventions and reputation.
- Humans handle ambiguous behavior and edge cases through trial-and-error.

AI agents, however, need:
- Strong provenance and integrity signals to safely select dependencies.
- Machine-readable capability metadata to avoid guessing URL grammar, parameters, defaults, and side effects.
- Deterministic, testable contracts to compose libraries reliably.

If PJDBC is not agent-friendly, agents may misuse it (incorrect URL composition, unsafe defaults), avoid it (lack of trust signals), or fork it (fragmentation).

## 3. Goals

G1. Make PJDBC safe to consume automatically from standard Java OSS channels (primarily Maven).  
G2. Publish a machine-readable description of PJDBC’s proxy-driver capabilities and constraints.  
G3. Provide a reusable conformance test suite that defines expected behavior per driver.  
G4. Preserve human usability; improvements should also benefit developer experience.  
G5. Maintain backwards compatibility where feasible, and document any breaking changes clearly.

## 4. Non-Goals

NG1. Creating a new “store” or proprietary registry.  
NG2. Building a GUI, end-user installer, or non-Java distribution format as a primary deliverable.  
NG3. Implementing every possible JDBC proxy feature; scope focuses on distribution, metadata, and verification.  
NG4. Guaranteeing full reproducible builds across all environments on day one (we will improve repeatability and provenance incrementally).

## 5. Users and Use Cases

Primary users:
- Library consumers building Java applications (humans).
- AI agents that select dependencies and generate integration code (machines).

Core use cases:
- An agent selects PJDBC from Maven and verifies authenticity and integrity.
- An agent discovers available driver prefixes and parameters without parsing prose docs.
- A developer (or agent) composes multiple PJDBC proxy drivers reliably.
- CI pipelines validate that PJDBC behavior is stable across releases via conformance tests.

## 6. Success Metrics

Release assurance:
- 100% of releases published with: signed artifacts, checksums, sources JAR, javadoc JAR.
- Each release includes an SBOM attached as an artifact (and/or published with classifiers).
- CI generates and publishes build provenance/attestations for release tags.

Agent discoverability:
- A machine-readable capability manifest is shipped in the release artifact.
- The manifest covers all shipped proxy drivers (prefix grammar, parameters, defaults, side effects, warnings).
- A simple “capabilities API” can output the same information at runtime.

Conformance:
- Each driver has conformance tests for key behaviors (URL parsing, parameter handling, expected side effects).
- CI runs conformance tests on a supported database matrix (start with one DB, expand later).

Adoption signals (optional/secondary):
- Reduced integration issues related to URL composition and defaults.
- More consistent downstream usage patterns (as seen in issues/discussions).

## 7. Scope and Phases

### Phase 1: High-Assurance Releases (Supply-Chain Baseline)

Objective: Make PJDBC a “high-assurance” dependency suitable for automated consumption.

Requirements:
1. Standardize release artifacts:
   - Publish primary JAR, sources JAR, javadoc JAR, checksums.
   - Ensure version tags map to published artifacts deterministically.

2. Signing:
   - Sign artifacts during release (standard Maven signing flow).
   - Publish signature files alongside artifacts.

3. SBOM:
   - Generate an SBOM for each release (CycloneDX recommended for Java ecosystems).
   - Attach SBOM to GitHub Releases and/or publish via Maven classifiers.

4. Provenance/attestation:
   - CI produces a provenance statement for each release tag referencing commit SHA and workflow.
   - Publish attestations as release assets.

5. Security hygiene:
   - Add SECURITY.md (reporting instructions, supported versions).
   - Enable automated dependency update PRs (Dependabot/Renovate).
   - Add CI checks: dependency vulnerability scanning + basic static analysis.

Deliverables:
- Release workflow updates (CI configuration).
- SECURITY.md.
- SBOM artifact per release.
- Provenance/attestation artifact per release.

### Phase 2: Agent-First Capability Contracts (Machine-Readable Metadata)

Objective: Make PJDBC’s features discoverable and composable without guessing.

Requirements:
6. Capability manifest file + schema:
   - Add `pjdbc.capabilities.json` describing all drivers.
   - Add `pjdbc.capabilities.schema.json` to define structure.
   - Version both with the PJDBC release and ship inside the JAR as a resource.
   - Also publish as a standalone release asset.

Manifest must include (minimum):
- Project name/version and supported Java baseline.
- For each driver:
  - Name and class (if applicable).
  - Prefix string and URL grammar.
  - Parameter list with types, defaults, and constraints.
  - Side effects and risk/warning flags (e.g., “logs SQL”, “duplicates operations”, “credential mapping”).
  - Compatibility notes (if any).

7. “Agent consumption” documentation:
   - Add a short README section explaining the manifest and a few safe composition recipes.
   - Provide copy/paste examples with explicit defaults and warnings.

8. Runtime introspection API:
   - Add a lightweight API (e.g., `PjdbcCapabilities`) that exposes the same info at runtime:
     - `listDrivers()`, `getDriver(prefix)`, `getManifestVersion()`.
   - Ensure it is stable and backward compatible.

Deliverables:
- JSON manifest + JSON Schema.
- README updates.
- Runtime introspection API.
- Basic validation that manifest matches shipped drivers (CI check).

### Phase 3: Conformance Suite (Behavior as a Distribution Primitive)

Objective: Publish tests that define expected behavior and help downstream users validate integrations.

Requirements:
9. Driver conformance tests:
   - For each driver, create black-box tests for:
     - URL parsing and nesting behavior.
     - Parameter parsing and default behavior.
     - Expected side effects (e.g., Readonly blocks DML by default).
     - Error messages (minimum: stable error type/category, not exact strings).

10. Reusable test module:
   - Publish a test fixture module (e.g., `pjdbc-conformance`) usable by downstream projects.
   - Document how to run tests locally and in CI.

11. Database test matrix (incremental):
   - Start with one database (e.g., Postgres) via containers.
   - Add others based on demand and maintainability.

Deliverables:
- Conformance test suite.
- `pjdbc-conformance` module or package.
- CI integration for conformance tests.

## 8. Detailed Requirements

### 8.1 Release & Provenance Requirements (Phase 1)

R1. Release pipeline produces and publishes:
- Primary artifact: PJDBC JAR
- Sources JAR
- Javadoc JAR
- Checksums (SHA-256 or standard Maven checksums)
- Signatures for artifacts

R2. SBOM is generated deterministically from the build and attached to the release.

R3. Provenance/attestation is generated by CI for tagged releases and published.

R4. SECURITY.md includes:
- How to report vulnerabilities
- Expected response timelines (best-effort)
- Supported versions policy

R5. CI includes:
- Dependency update automation enabled
- Dependency vulnerability scanning
- Static analysis baseline (lightweight at first)

### 8.2 Capability Manifest Requirements (Phase 2)

R6. Manifest file name and location:
- JAR resource path: `META-INF/pjdbc/pjdbc.capabilities.json`
- Schema path: `META-INF/pjdbc/pjdbc.capabilities.schema.json`
- Release asset: `pjdbc.capabilities.json` and `pjdbc.capabilities.schema.json`

R7. Manifest supports composition:
- Explicitly models that a driver “wraps/forwards” to another JDBC URL where applicable.
- Captures delimiter rules (e.g., multi-target separators).

R8. Manifest captures safety:
- Each driver includes `sideEffects` and `riskFlags` fields.
- Drivers with write amplification (tee/replication) or credential rewriting must be flagged.

R9. Manifest is validated in CI:
- JSON schema validation.
- Optional: unit test asserting that all shipped drivers are listed in manifest.

R10. Runtime API returns:
- The manifest as structured objects.
- The raw manifest JSON (optional).

### 8.3 Conformance Requirements (Phase 3)

R11. Conformance tests define minimum guarantees:
- Parameter defaults must match manifest.
- URL grammar must match manifest.
- Core “safety behaviors” must be stable across patch releases.

R12. Tests are runnable without special infrastructure beyond containers:
- `./mvnw test` should run with a documented profile to enable containers.

R13. `pjdbc-conformance` is published and versioned alongside PJDBC.

## 9. Milestones

M1. Phase 1 complete:
- First “hardened” release published with signed artifacts, SBOM, provenance.
- SECURITY.md merged.
- CI security checks enabled.

M2. Phase 2 complete:
- Manifest + schema shipped in release and validated in CI.
- README updated with agent-first section.
- Runtime introspection API released.

M3. Phase 3 complete:
- Conformance tests for all built-in drivers.
- Reusable conformance module published.
- DB matrix running in CI (at least one DB).

## 10. Risks and Mitigations

Risk: Additional release complexity increases maintainer burden.  
Mitigation: Automate as much as possible in CI; keep steps minimal and repeatable.

Risk: Manifest becomes stale or inaccurate.  
Mitigation: CI validation; generate parts of manifest from code annotations where feasible.

Risk: Conformance tests are flaky due to container environments.  
Mitigation: Start small (one DB), use stable images, add retries/timeouts, keep tests black-box and deterministic.

Risk: Backward compatibility challenges if behavior is underspecified today.  
Mitigation: Codify current behavior explicitly; for changes, version manifest and document breaking changes with migration notes.

## 11. Decisions (Resolved)

1. **Release channel:** Maven Central is the authoritative release channel.
2. **Java version:** Java 21 is the supported baseline.
3. **Driver stability:** All built-in drivers are considered "stable contract".
4. **Stability levels in manifest:** Yes, include `stability` field in driver manifest entries.
5. **Performance invariants:** Yes, conformance suite will include performance invariants (timeouts, pooling semantics) in addition to functional behavior.

## 12. Appendix A: Capability Manifest Structure (Proposed)

Proposed top-level fields:
- `project`: string
- `version`: string (PJDBC version)
- `java`: string (e.g., "21+")
- `drivers`: array of driver objects
- `generatedAt`: ISO timestamp (optional)
- `schemaVersion`: string (optional)

Driver object (minimum):
- `name`: string
- `prefix`: string
- `urlGrammar`: object/string describing how URLs are formed
- `forwardsTo`: boolean or description (e.g., “next JDBC URL”)
- `params`: object (each param has type/default/constraints)
- `sideEffects`: array of strings
- `riskFlags`: array of strings
- `examples`: array of strings (optional)
- `stability`: string (required, one of: "stable", "experimental")
- `performanceInvariants`: object (optional, for conformance testing)

## 13. Appendix B: Initial Backlog (Issue Seeds)

1. Release hardening: signed artifacts + sources/javadoc/checksums  
2. SBOM generation and publication  
3. CI provenance/attestations for releases  
4. SECURITY.md + supported versions policy  
5. Dependency update automation + vulnerability scanning  
6. Capability manifest + JSON Schema (ship in artifact + release asset)  
7. README: “Agent-first usage” section + composition recipes  
8. Runtime introspection API for capabilities  
9. Conformance tests per driver (start with safety-critical drivers)  
10. Publish `pjdbc-conformance` module and docs  
