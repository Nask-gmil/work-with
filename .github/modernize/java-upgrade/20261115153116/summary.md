# Java Upgrade Summary: work-with

## Result

The project now targets Java 25 LTS. Maven compilation, Docker build/runtime base images, and local setup documentation were updated.

## Changes

- Set Maven `java.version` to 25.
- Changed both Docker stages to Eclipse Temurin 25.
- Updated the README backend stack and JDK prerequisite to Java 25.

## Verification

- Runtime: Microsoft OpenJDK 25.0.5.0.2 LTS
- Build tool: Maven Wrapper 3.9.16
- Clean test compilation: passed in an isolated project copy.
- Tests: 138/138 passed; 0 failures, 0 errors, 0 skipped.

## Limitations

- Baseline tests were skipped because JDK 21 is not installed.
- The workspace `clean` command could not delete `target/classes` while VS Code Java tooling held it; clean build and tests passed in a temporary copy.
- Docker image build was not run because the Docker daemon was unreachable.
- Dedicated CVE validation was unavailable; no CVE scan result is claimed.
