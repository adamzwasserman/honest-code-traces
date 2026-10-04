#!/usr/bin/env bash
# Copies the output of run-everything.sh from the machine that ran it into
# measured/, under the file names choose_medians.py reads.
# Usage: bash collect_results.sh <ssh-host> [remote-results-dir]
set -euo pipefail
HOST="$1"
REMOTE="${2:-rerun}"
M="$(cd "$(dirname "$0")" && pwd)/measured"
mkdir -p "$M/runs"
for i in $(seq 1 10); do
  mkdir -p "$M/runs/run$i"
  scp -q "$HOST:$REMOTE/runs/run$i/v2/*.json" "$M/runs/run$i/"
done
scp -q "$HOST:$REMOTE/checks/cpp.json" "$M/googlebenchmark-cpp.json"
scp -q "$HOST:$REMOTE/checks/go.txt" "$M/go-testing-bench.txt"
scp -q "$HOST:$REMOTE/checks/ts.json" "$M/mitata-typescript.json"
scp -q "$HOST:$REMOTE/checks/dart.txt" "$M/dart-benchmark-harness.txt"
scp -q "$HOST:$REMOTE/checks/dart_aot.txt" "$M/dart-aot-benchmark-harness.txt"
scp -q "$HOST:$REMOTE/checks/python-pyperf.json" "$M/python-pyperf.json"
scp -q "$HOST:$REMOTE/checks/ruby.txt" "$M/ruby-benchmark-ips.txt"
scp -q "$HOST:$REMOTE/checks/php.txt" "$M/php-phpbench.txt"
scp -q "$HOST:$REMOTE/checks/swift.txt" /tmp/swift_raw.txt
grep -v 'no version information' /tmp/swift_raw.txt | grep -v $'\x1b\\[1A' > "$M/swift-package-benchmark.txt"
for f in java java-tiered-off kotlin kotlin-tiered-off; do
  scp -q "$HOST:$REMOTE/jmh-work/out/$f.json" "$M/jmh-$f.json"
done
scp -q "$HOST:$REMOTE/bdn.txt" /tmp/bdn_raw.txt
grep -E '^(BenchmarkDotNet|\.NET SDK|Intel|\| )' /tmp/bdn_raw.txt > "$M/benchmarkdotnet.txt"
echo "collected into $M"
