# hegel-java task runner.
#
# `just coverage` enforces 100% instruction + branch coverage and exits
# non-zero otherwise (it delegates to JaCoCo's check goal, configured in pom.xml).

# List available recipes.
default:
    @just --list

# Build libhegel from a sibling hegel-rust checkout (no-op if absent).
build-libhegel:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ -d ../hegel-rust ]; then
        echo "Building libhegel from ../hegel-rust ..."
        (cd ../hegel-rust && cargo build --release -p hegeltest-c)
    else
        echo "No sibling ../hegel-rust checkout; the auto-downloader will fetch libhegel."
    fi

# Compile the library and tests.
build:
    mvn -B -q compile test-compile

# Run the full test suite (no coverage gate).
test:
    mvn -B test

# Run tests with 100% coverage enforcement. Fails if coverage < 100%.
coverage:
    mvn -B verify

# Run the conformance/behaviour suite against the real libhegel.
conformance:
    mvn -B test -Dtest='*Conformance*,*Behaviour*'

# Auto-format sources.
format:
    mvn -B com.spotify.fmt:fmt-maven-plugin:format

# Check formatting (fails if not formatted).
lint:
    mvn -B com.spotify.fmt:fmt-maven-plugin:check

# Build Javadoc.
docs:
    mvn -B -q javadoc:javadoc

# Full CI check: lint + coverage + docs.
check: lint coverage docs

# Remove build artifacts.
clean:
    mvn -B -q clean
