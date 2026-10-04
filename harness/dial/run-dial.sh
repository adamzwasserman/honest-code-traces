#!/usr/bin/env bash
# Measures the same program at different optimizer settings, for the companion
# site's optimizer dial: Java as interpreter only, first compiler tier only,
# second tier only and full tiered JIT; a Java warm-up timeline; C++ at -O0 and
# -O2; PHP with no JIT, the opcode cache only, and two JIT modes.
# Needs the JMH build from harness/jmh/run-jmh.sh in $JMH_WORK (default
# ~/jmh-work) and the honest-traces-v2 image. Results land in $OUT (default ~/dial).
# Usage (from the repo root): bash harness/dial/run-dial.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${OUT:-$HOME/dial}"
JMH_WORK="${JMH_WORK:-$HOME/jmh-work}"
mkdir -p "$OUT"
docker run --rm -v "$ROOT/harness:/h" -v "$JMH_WORK:/w" -v "$OUT:/out" honest-traces-v2 bash -c '
  set -e
  CP="/w/java:/w/lib/*"
  PATTERN="JavaBench.(dishonestNoTimestamp|honest)"
  for mode in default:- interpreter:-Xint tier1:-XX:TieredStopAtLevel=1 tier2:-XX:-TieredCompilation; do
    name=${mode%%:*}; flag=${mode#*:}
    ARGS=""; [ "$flag" != "-" ] && ARGS="-jvmArgsAppend $flag"
    java -cp "$CP" org.openjdk.jmh.Main "$PATTERN" -f 3 -wi 3 -w 1s -i 5 -r 1s $ARGS -rf json -rff /out/java-$name.json > /out/java-$name.log 2>&1
  done
  java -cp "$CP" org.openjdk.jmh.Main "$PATTERN" -f 1 -wi 0 -i 40 -r 100ms > /out/java-timeline.log 2>&1
  apt-get update -qq >/dev/null; apt-get install -y -qq libbenchmark-dev >/dev/null 2>&1
  for opt in O0 O2; do
    g++ -$opt -std=c++17 /h/checks/bench_cpp.cpp -lbenchmark -lpthread -o /tmp/bench_$opt
    /tmp/bench_$opt --benchmark_repetitions=5 --benchmark_format=json --benchmark_out=/out/cpp-$opt.json > /dev/null
  done
  for mode in "default|" "opcache|-d opcache.enable_cli=1" "jit-tracing|-d opcache.enable_cli=1 -d opcache.jit_buffer_size=64M -d opcache.jit=tracing" "jit-function|-d opcache.enable_cli=1 -d opcache.jit_buffer_size=64M -d opcache.jit=function"; do
    name=${mode%%|*}; args=${mode#*|}
    for i in 1 2 3; do php $args /h/php/harness_v2.php > /out/php-$name-$i.json; done
  done
'
echo done > "$OUT/done"
