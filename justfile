# Recipes assume a POSIX shell; on Windows run them under Git Bash.
set windows-shell := ["bash", "-uc"]

build-libhegel:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ -d ../hegel-rust ]; then
        (cd ../hegel-rust && cargo build --release -p hegeltest-c)
        case "$(uname -s)" in
            Darwin*) lib=libhegel.dylib ;;
            MINGW*|MSYS*|CYGWIN*) lib=hegel.dll ;; # cargo emits no lib prefix on Windows
            *) lib=libhegel.so ;;
        esac
        echo "Built libhegel. Point the tests at it with:"
        echo "  export HEGEL_LIBHEGEL_PATH=$(cd ../hegel-rust && pwd)/target/release/$lib"
    else
        echo "No sibling ../hegel-rust checkout; tests use the libhegel bundled in the jar."
    fi

build:
    mvn -B -q compile test-compile

test:
    mvn -B test

coverage:
    mvn -B verify

test-jna:
    mvn -B -pl hegel-jna -am test

coverage-jna:
    mvn -B -pl hegel-jna -am verify

conformance:
    mvn -B test -Dtest='*Conformance*,*Behaviour*'

format:
    mvn -B spotless:apply

lint:
    mvn -B spotless:check

check-docs:
    mvn -B -q javadoc:javadoc

docs:
    mvn -B -q javadoc:javadoc
    open hegel/target/reports/apidocs/index.html

clean:
    mvn -B -q clean

check: lint coverage check-docs
