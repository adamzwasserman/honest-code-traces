#!/usr/bin/env bash
# Runs the benchmarking-tool checks for C++, Go and TypeScript inside the
# honest-traces-v2 image. Needs network for apt, the Go toolchain and npm.
# Usage (from the repo root): bash harness/checks/run-checks.sh [cpp|go|ts ...]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${OUT:-$HOME/checks-out}"
mkdir -p "$OUT"
WHICH="${*:-cpp go ts}"
docker run --rm -e WHICH="$WHICH" -v "$ROOT/harness:/h" -v "$OUT:/out" honest-traces-v2 bash -c '
  set -e
  for w in $WHICH; do
    case $w in
      cpp)
        apt-get update -qq >/dev/null
        apt-get install -y -qq libbenchmark-dev >/dev/null
        g++ -O2 -std=c++17 /h/checks/bench_cpp.cpp -lbenchmark -lpthread -o /tmp/bench_cpp
        /tmp/bench_cpp --benchmark_repetitions=10 --benchmark_format=json --benchmark_out=/out/cpp.json > /out/cpp.log
        ;;
      go)
        mkdir -p /tmp/gobench && cd /tmp/gobench
        cp /h/go/harness_v2.go /h/checks/bench_go_test.go .
        printf "module bench\n\ngo 1.24\n" > go.mod
        go test -run "^\$" -bench . -count 10 -benchtime 1s > /out/go.txt
        ;;
      ts)
        mkdir -p /tmp/tsbench && cd /tmp/tsbench
        sed "\$d" /h/typescript/harness_v2.ts > lib.ts
        tail -1 /h/typescript/harness_v2.ts | grep -qx "main();"
        echo "export { dishonestScenario, honestScenario };" >> lib.ts
        cp /h/checks/bench_ts.ts .
        npm init -y >/dev/null
        npm pkg set type=module >/dev/null
        npm install mitata >/dev/null 2>&1
        tsx bench_ts.ts > /out/ts.json
        ;;
    esac
  done
'
