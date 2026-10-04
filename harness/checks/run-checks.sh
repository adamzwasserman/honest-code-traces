#!/usr/bin/env bash
# Runs the benchmarking-tool checks for C++, Go and TypeScript inside the
# honest-traces-v2 image. Needs network for apt, the Go toolchain and npm.
# Usage (from the repo root): bash harness/checks/run-checks.sh [cpp|go|ts|swift|dart ...]
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
      swift)
        # The Swift 6.0.3 toolchain wants libxml2.so.2, and Ubuntu 26.04 ships .so.16.
        apt-get update -qq >/dev/null
        apt-get install -y -qq git >/dev/null 2>&1
        ln -sf /usr/lib/x86_64-linux-gnu/libxml2.so.16 /usr/lib/x86_64-linux-gnu/libxml2.so.2
        mkdir -p /tmp/swiftbench/Benchmarks/ScenarioBench && cd /tmp/swiftbench
        cp /h/checks/swift/Package.swift .
        head -206 /h/swift/harness_v2.swift > Benchmarks/ScenarioBench/Scenario.swift
        cp /h/checks/swift/ScenarioBench.swift Benchmarks/ScenarioBench/
        BENCHMARK_DISABLE_JEMALLOC=true swift package --allow-writing-to-package-directory benchmark > /out/swift.txt 2>&1
        ;;
      dart)
        mkdir -p /tmp/dartbench && cd /tmp/dartbench
        printf "name: bench\nenvironment:\n  sdk: ^3.5.0\n" > pubspec.yaml
        dart pub add benchmark_harness >/dev/null
        sed "s/^void main()/void harnessMain()/" /h/dart/harness_v2.dart > lib.dart
        cp /h/checks/bench_dart.dart .
        dart run bench_dart.dart > /out/dart.txt
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
