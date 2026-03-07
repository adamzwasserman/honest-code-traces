#!/usr/bin/env bash
set -euo pipefail

RESULTS_DIR="/traces/results"
mkdir -p "$RESULTS_DIR"

echo "=== Honest Code Trace Harnesses ==="
echo ""

echo "[1/4] Java..."
java -cp /traces/harness/java Harness | tee "$RESULTS_DIR/java.json"
echo ""

echo "[2/4] Python..."
python /traces/harness/python/harness.py | tee "$RESULTS_DIR/python.json"
echo ""

echo "[3/4] TypeScript..."
tsx /traces/harness/typescript/harness.ts | tee "$RESULTS_DIR/typescript.json"
echo ""

echo "[4/4] Go..."
cd /traces/harness/go && go run harness.go | tee "$RESULTS_DIR/go.json"
echo ""

echo "=== Done. Results in results/ ==="
