#!/usr/bin/env bash
set -euo pipefail

RESULTS_DIR="/traces/results"
mkdir -p "$RESULTS_DIR"

echo "=== Honest Code Trace Harnesses (11 languages) ==="
echo ""

echo "[1/11] Java..."
java -cp /traces/harness/java Harness | tee "$RESULTS_DIR/java.json"
echo ""

echo "[2/11] Kotlin..."
java -jar /traces/harness/kotlin/harness.jar | tee "$RESULTS_DIR/kotlin.json"
echo ""

echo "[3/11] C#..."
dotnet run --project /traces/harness/csharp -c Release --no-build --nologo | tee "$RESULTS_DIR/csharp.json"
echo ""

echo "[4/11] Python..."
python /traces/harness/python/harness.py | tee "$RESULTS_DIR/python.json"
echo ""

echo "[5/11] TypeScript..."
tsx /traces/harness/typescript/harness.ts | tee "$RESULTS_DIR/typescript.json"
echo ""

echo "[6/11] Go..."
cd /traces/harness/go && go run harness.go | tee "$RESULTS_DIR/go.json"
echo ""

echo "[7/11] PHP..."
php /traces/harness/php/harness.php | tee "$RESULTS_DIR/php.json"
echo ""

echo "[8/11] Ruby..."
ruby /traces/harness/ruby/harness.rb | tee "$RESULTS_DIR/ruby.json"
echo ""

echo "[9/11] Swift..."
if [ -x /traces/harness/swift/harness ]; then
  /traces/harness/swift/harness | tee "$RESULTS_DIR/swift.json"
else
  echo '{"language": "swift", "error": "Swift not available on this platform"}' | tee "$RESULTS_DIR/swift.json"
fi
echo ""

echo "[10/11] Dart..."
dart run /traces/harness/dart/harness.dart | tee "$RESULTS_DIR/dart.json"
echo ""

echo "[11/11] C++..."
/traces/harness/cpp/harness | tee "$RESULTS_DIR/cpp.json"
echo ""

echo "=== Done. Results in results/ ==="
