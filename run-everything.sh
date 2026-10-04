#!/usr/bin/env bash
# Rebuilds the image and reruns every measurement: ten runs of the 12 hand-written
# harnesses, the benchmarking-tool checks for every language, JMH and
# BenchmarkDotNet. It takes about two hours. Nothing else should run on the
# machine meanwhile. Results land in $OUT (default ~/rerun); copy them into
# measured/ afterwards and run choose_medians.py.
# Usage (from the repo root): bash run-everything.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$HOME/rerun}"
mkdir -p "$OUT/runs" "$OUT/checks"
cd "$ROOT"
rm -f "$OUT/done"
docker build -t honest-traces-v2 . > "$OUT/build.log" 2>&1
for i in $(seq 1 10); do
  mkdir -p "$OUT/runs/run$i"
  docker run --rm -v "$OUT/runs/run$i:/traces/results" honest-traces-v2 bash run-all-v2.sh > "$OUT/runs/run$i.log" 2>&1
done
OUT="$OUT/checks" bash harness/checks/run-checks.sh cpp go ts swift dart dartaot python ruby php > "$OUT/checks.log" 2>&1
WORK="$OUT/jmh-work" bash harness/jmh/run-jmh.sh > "$OUT/jmh.log" 2>&1
OUT="$OUT/bdn.txt" bash harness/bdn/run-bdn.sh
echo done > "$OUT/done"
