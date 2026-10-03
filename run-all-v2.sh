#!/usr/bin/env bash
set -euo pipefail

RESULTS_DIR="/traces/results/v2"
mkdir -p "$RESULTS_DIR"

echo "=== Honest Code Trace Harnesses v2 (11 languages) ==="

echo "[1/11] Java..."
java -cp /traces/harness/java HarnessV2 | tee "$RESULTS_DIR/java.json"

echo "[2/11] Kotlin..."
java -jar /traces/harness/kotlin/harness_v2.jar | tee "$RESULTS_DIR/kotlin.json"

echo "[3/11] C#..."
dotnet run --project /traces/harness/csharp_v2 -c Release --no-build --nologo | tee "$RESULTS_DIR/csharp.json"

echo "[4/11] Python..."
python /traces/harness/python/harness_v2.py | tee "$RESULTS_DIR/python.json"

echo "[5/11] TypeScript..."
tsx /traces/harness/typescript/harness_v2.ts | tee "$RESULTS_DIR/typescript.json"

echo "[6/11] Go..."
cd /traces/harness/go && go run harness_v2.go | tee "$RESULTS_DIR/go.json"

echo "[7/11] PHP..."
php /traces/harness/php/harness_v2.php | tee "$RESULTS_DIR/php.json"

echo "[8/11] Ruby..."
ruby /traces/harness/ruby/harness_v2.rb | tee "$RESULTS_DIR/ruby.json"

echo "[9/11] Swift..."
if [ -x /traces/harness/swift/harness_v2 ]; then
  /traces/harness/swift/harness_v2 | tee "$RESULTS_DIR/swift.json"
else
  echo '{"language": "swift", "error": "Swift not available on this platform"}' | tee "$RESULTS_DIR/swift.json"
fi

echo "[10/11] Dart..."
dart run /traces/harness/dart/harness_v2.dart | tee "$RESULTS_DIR/dart.json"

echo "[11/11] C++..."
/traces/harness/cpp/harness_v2 | tee "$RESULTS_DIR/cpp.json"

echo "=== Done. Results in results/v2/ ==="
