#!/usr/bin/env bash
# Runs Experiment 1 of preregistration/memory-layout.md in C++, Java and Go,
# inside the honest-traces-v2 image, pinned to one core. Results land in
# $OUT (default ~/layout), one JSON line per layout and size.
# Usage (from the repo root): bash harness/layout/run-layout.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${OUT:-$HOME/layout}"
mkdir -p "$OUT"
rm -f "$OUT/done"
docker run --rm --cpuset-cpus=5 -v "$ROOT/harness/layout:/h" -v "$OUT:/out" honest-traces-v2 bash -c '
  set -e
  cd /tmp
  g++ -O2 -std=c++17 /h/layout_sweep.cpp -o layout_sweep && ./layout_sweep > /out/cpp.jsonl
  javac -d /tmp /h/LayoutSweep.java && java -Xmx10g -Xms10g -cp /tmp LayoutSweep > /out/java.jsonl
  cd /h/go && go run layout_sweep.go > /out/go.jsonl
'
echo done > "$OUT/done"
