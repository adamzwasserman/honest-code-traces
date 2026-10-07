#!/usr/bin/env bash
# Sums a contiguous array of N integers in Python, Ruby, PHP and Node, inside
# the honest-traces-v2 image on one pinned core. Results land in $OUT
# (default ~/layout-others) as one JSON line per idiom and size.
# Usage (from the repo root): bash harness/layout/others/run-others.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="${OUT:-$HOME/layout-others}"
mkdir -p "$OUT"; rm -f "$OUT/done"
docker run --rm --cpuset-cpus=5 -v "$ROOT/harness/layout/others:/h" -v "$OUT:/out" honest-traces-v2 bash -c '
  python3 /h/contiguous.py > /out/python.jsonl
  ruby /h/contiguous.rb > /out/ruby.jsonl
  php /h/contiguous.php > /out/php.jsonl
  node /h/contiguous.js > /out/javascript.jsonl
'
echo done > "$OUT/done"
