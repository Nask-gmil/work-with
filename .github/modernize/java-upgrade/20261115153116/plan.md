# Upgrade Plan: work-with (20261115153116)

- **Generated**: 2026-11-16
- **HEAD Branch**: appmod/java-upgrade-20261115153116
- **HEAD Commit ID**: Recorded by version control at execution time

## Available Tools

**JDKs**
- JDK 21: not available (baseline will be skipped)
- JDK 25.0.5.0.2: C:\Users\nakas\AppData\Local\jdks\jdk-25\bin (latest LTS patch, used for final verification)

**Build Tools**
- Maven Wrapper: 3.9.16 (configured in .mvn/wrapper/maven-wrapper.properties)

## Guidelines

- Upgrade the Java runtime to the latest LTS version, Java 25.

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

## Options

- Working branch: appmod/java-upgrade-20261115153116
- Run tests before and after the upgrade: true

## Upgrade Goals

- Java runtime: 25 (latest LTS)

## Technology Stack

| Technology/Dependency | Current | Min Compatible Version | Why Incompatible |
| --------------------- | ------- | ----------------------- | ---------------- |
| Java | 21 | 25 | User requested Java 25 LTS |
| Spring Boot | 3.5.16 | 3.5.16 | Retained; Java runtime-only upgrade requested |
| Maven Wrapper | 3.9.16 | 3.9.16 | Already suitable for the target |
| SQLite JDBC | 3.53.2.0 | 3.53.2.0 | No Java target incompatibility identified |
| Docker build/runtime images | Eclipse Temurin 21 | Eclipse Temurin 25 | Container runtime should match the target |

## Derived Upgrades

- Set Maven compilation to Java 25, update both Docker stages to Temurin 25, and update README environment references.
- No Kotlin, Gradle, or CI pipeline configuration was detected.
- Keep Spring Boot 3.5.16 and Maven Wrapper 3.9.16; no framework or build-tool upgrade is required for the requested runtime change.

## Impact Analysis

### Dependency Changes

| File | Dependency | Current | Action | Target | Reason |
|------|-----------|---------|--------|--------|--------|
| pom.xml | java.version | 21 | upgrade | 25 | Configure Java 25 compilation |
| Dockerfile | Eclipse Temurin build image | 21-jdk | upgrade | 25-jdk | Build with target JDK |
| Dockerfile | Eclipse Temurin runtime image | 21-jre | upgrade | 25-jre | Run on target JRE |

### Source Code Changes

| File | Location | Current | Required Change | Reason |
|------|----------|---------|----------------|--------|
| None | src/main/java | No JDK-internal imports or identified Java 25-removed APIs | None | Source scan found no compatibility rewrite |

### Configuration Changes

| File | Property/Setting | Current | Required Change | Reason |
|------|------------------|---------|----------------|--------|
| None | — | No runtime-specific application configuration found | None | JDK selection is controlled by Maven and Docker |

### CI/CD Changes

| File | Location | Current | Required Change |
|------|----------|---------|----------------|
| None | — | No CI/CD configuration detected | None |

### Documentation Changes

| File | Location | Current | Required Change | Reason |
|------|----------|---------|----------------|--------|
| README.md | Backend technology row | Java 21 | Change to Java 25 | Keep technology stack accurate |
| README.md | Local prerequisites | JDK 21 | Change to JDK 25 | Document required build runtime |

### Risks & Warnings

- **Baseline unavailable**: JDK 21 is not installed, so baseline compilation/tests cannot be established. **Mitigation**: Run all post-upgrade checks on installed JDK 25.0.5.0.2 and explicitly note the lack of a baseline.
- **Docker runtime validation**: A successful Maven test does not verify the container image. **Mitigation**: Attempt a Docker build if Docker is installed; otherwise document the skipped container check.
- **CVE scanner unavailable**: The dedicated dependency-CVE scanning action is not available in this session's tools. **Mitigation**: Resolve dependencies and compile, but do not claim a CVE scan was completed.

## Upgrade Steps

- Step 1: Setup Environment
  - **Rationale**: Confirm the target JDK and Maven Wrapper before changing configuration.
  - **Changes to Make**: Use installed JDK 25.0.5.0.2 and Maven Wrapper 3.9.16.
  - **Verification**: `java -version` and `./mvnw.cmd -version`; verified Java 25.0.5.0.2 and Maven 3.9.16.
- Step 2: Setup Baseline
  - **Rationale**: Record pre-upgrade acceptance results when the base JDK is available.
  - **Changes to Make**: No files; skip because JDK 21 is unavailable.
  - **Verification**: Skipped; baseline cannot run without JDK 21.
- Step 3: Upgrade Runtime Configuration and Documentation
  - **Rationale**: Align compiler target, container build/runtime, and developer documentation with Java 25.
  - **Changes to Make**: Apply Dependency Changes and Documentation Changes above.
  - **Verification**: Review changes, then run `./mvnw.cmd clean test-compile -q` using JDK 25.0.5.0.2 in an isolated copy because VS Code locks the workspace output.
- Step 4: CVE Validation & Fix
  - **Rationale**: Check dependencies for known vulnerabilities after the runtime changes.
  - **Changes to Make**: Run a dependency security scan if an available scanner exists; fix reported issues with minimal compatible version updates.
  - **Verification**: Dedicated CVE scanner is unavailable in this session; no CVE scan result is claimed.
- Step 5: Final Validation
  - **Rationale**: Confirm Java 25 compilation and test success.
  - **Changes to Make**: Resolve build/test failures and ensure targeted Java version references are current.
  - **Verification**: `./mvnw.cmd clean test-compile -q` and `./mvnw.cmd test -q` passed using JDK 25.0.5.0.2 in an isolated project copy (138 tests, 0 failures/errors/skips). Docker daemon was unavailable, so image build was not run.
