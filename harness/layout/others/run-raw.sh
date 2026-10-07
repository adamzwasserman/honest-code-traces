#!/usr/bin/env bash
# Times raw element access, with no arithmetic, in C++, Python, Ruby, PHP and
# Node inside the honest-traces-v2 image on one pinned core. Results land in
# $OUT (default ~/layout-raw) as one JSON line per idiom and size.
# Usage (from the repo root): bash harness/layout/others/run-raw.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="${OUT:-$HOME/layout-raw}"
mkdir -p "$OUT"; rm -f "$OUT/done"
docker run --rm --cpuset-cpus=5 -v "$ROOT/harness/layout/others:/h" -v "$OUT:/out" honest-traces-v2 bash -c '
  cd /tmp && g++ -O2 -std=c++17 /h/raw.cpp -o raw && ./raw > /out/cpp.jsonl
  python3 /h/raw.py > /out/python.jsonl
  ruby /h/raw.rb > /out/ruby.jsonl
  php /h/raw.php > /out/php.jsonl
  node /h/raw.js > /out/javascript.jsonl
'
echo done > "$OUT/done"
