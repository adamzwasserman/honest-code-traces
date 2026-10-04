#!/usr/bin/env bash
# Runs JMH over the hand-written Java and Kotlin harness code, inside the
# honest-traces image, once with the default tiered compilation and once with
# it off. The first step downloads the JMH jars with Maven.
# Usage (from the repo root): bash harness/jmh/run-jmh.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${WORK:-$HOME/jmh-work}"
mkdir -p "$WORK/lib" "$WORK/out"
docker run --rm -v "$ROOT/harness/jmh:/src" -v "$WORK/lib:/lib-out" maven:3.9-eclipse-temurin-21 \
  mvn -q -f /src/pom.xml dependency:copy-dependencies -DoutputDirectory=/lib-out
docker run --rm -v "$ROOT/harness:/h" -v "$WORK:/w" honest-traces-v2 bash -c '
  set -e
  # JMH needs a named package, so build from copies with a package line added.
  CP="/w/lib/*"
  rm -rf /w/src /w/java /w/kotlin
  mkdir -p /w/src/java /w/src/kotlin /w/java /w/kotlin
  for f in /h/java/HarnessV2.java /h/jmh/JavaBench.java; do
    { echo "package harness;"; cat "$f"; } > "/w/src/java/$(basename "$f")"
  done
  { echo "package harness"; cat /h/kotlin/harness_v2.kt; } > /w/src/kotlin/harness_v2.kt
  { echo "package harness;"; cat /h/jmh/KotlinBench.java; } > /w/src/kotlin/KotlinBench.java
  javac -cp "$CP" -d /w/java /w/src/java/*.java
  for mode in default tiered-off; do
    if [ $mode = tiered-off ]; then ARGS="-jvmArgsAppend -XX:-TieredCompilation"; SUFFIX="-tiered-off"; else ARGS=""; SUFFIX=""; fi
    java -cp "/w/java:$CP" org.openjdk.jmh.Main JavaBench -f 3 -wi 5 -w 1s -i 8 -r 1s $ARGS -rf json -rff /w/out/java$SUFFIX.json
  done
  kotlinc /w/src/kotlin/harness_v2.kt -d /w/kotlin
  KLIB=$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib/kotlin-stdlib.jar
  javac -cp "$CP:/w/kotlin:$KLIB" -d /w/kotlin /w/src/kotlin/KotlinBench.java
  for mode in default tiered-off; do
    if [ $mode = tiered-off ]; then ARGS="-jvmArgsAppend -XX:-TieredCompilation"; SUFFIX="-tiered-off"; else ARGS=""; SUFFIX=""; fi
    java -cp "/w/kotlin:$KLIB:$CP" org.openjdk.jmh.Main KotlinBench -f 3 -wi 5 -w 1s -i 8 -r 1s $ARGS -rf json -rff /w/out/kotlin$SUFFIX.json
  done
'
