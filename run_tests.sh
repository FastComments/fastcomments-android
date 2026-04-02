#!/usr/bin/env bash
#
# Test runner for FastComments Android.
#
# Usage:
#   ./run_tests.sh sdk    # Robolectric unit + integration tests
#   ./run_tests.sh dual   # Dual-emulator live event tests
#   ./run_tests.sh all    # Both
#

set -euo pipefail
cd "$(dirname "$0")"

run_sdk_tests() {
    echo "=== Running SDK unit/integration tests (Robolectric) ==="
    ./gradlew :libraries:sdk:test
}

run_dual_tests() {
    echo "=== Running dual-emulator live event tests ==="
    python3 run_dual_emu_tests.py "$@"
}

case "${1:-all}" in
    sdk)
        run_sdk_tests
        ;;
    dual)
        shift
        run_dual_tests "$@"
        ;;
    all)
        run_sdk_tests
        shift 2>/dev/null || true
        run_dual_tests "$@"
        ;;
    *)
        echo "Usage: $0 {sdk|dual|all} [--e2e-key KEY]"
        exit 1
        ;;
esac
