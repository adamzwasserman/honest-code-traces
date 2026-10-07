#!/usr/bin/env bash
set -euo pipefail

RESULTS_DIR="/traces/results/v2"
mkdir -p "$RESULTS_DIR"

echo "=== Honest Code Trace Harnesses v2 (11 languages, Dart twice) ==="

echo "[1/12] Java..."
java -cp /traces/harness/java HarnessV2 | tee "$RESULTS_DIR/java.json"

echo "[2/12] Kotlin..."
java -jar /traces/harness/kotlin/harness_v2.jar | tee "$RESULTS_DIR/kotlin.json"

echo "[3/12] C#..."
dotnet run --project /traces/harness/csharp_v2 -c Release --no-build --nologo | tee "$RESULTS_DIR/csharp.json"

echo "[4/12] Python..."
python /traces/harness/python/harness_v2.py | tee "$RESULTS_DIR/python.json"

echo "[5/12] TypeScript..."
tsx /traces/harness/typescript/harness_v2.ts | tee "$RESULTS_DIR/typescript.json"

echo "[6/12] Go..."
cd /traces/harness/go && go run harness_v2.go | tee "$RESULTS_DIR/go.json"

echo "[7/12] PHP..."
php /traces/harness/php/harness_v2.php | tee "$RESULTS_DIR/php.json"

echo "[8/12] Ruby..."
ruby /traces/harness/ruby/harness_v2.rb | tee "$RESULTS_DIR/ruby.json"

echo "[9/12] Swift..."
if [ -x /traces/harness/swift/harness_v2 ]; then
  /traces/harness/swift/harness_v2 | tee "$RESULTS_DIR/swift.json"
else
  echo '{"language": "swift", "error": "Swift not available on this platform"}' | tee "$RESULTS_DIR/swift.json"
fi

echo "[10/12] Dart..."
dart run /traces/harness/dart/harness_v2.dart | tee "$RESULTS_DIR/dart.json"

echo "[11/12] Dart (precompiled)..."
/traces/harness/dart/harness_v2_aot | tee "$RESULTS_DIR/dart_aot.json"

echo "[12/12] C++..."
/traces/harness/cpp/harness_v2 | tee "$RESULTS_DIR/cpp.json"

echo "=== Done. Results in results/v2/ ==="
