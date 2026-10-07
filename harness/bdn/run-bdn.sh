#!/usr/bin/env bash
# Runs BenchmarkDotNet over the C# harness scenario, inside the honest-traces
# image, with tiered compilation on (the default) and off. The table goes to
# the file named by OUT (default ~/bdn-out.txt).
# Usage (from the repo root): bash harness/bdn/run-bdn.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${OUT:-$HOME/bdn-out.txt}"
docker run --rm -e DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 -e DOTNET_CLI_TELEMETRY_OPTOUT=1 \
  -v "$ROOT/harness:/h" -w /h/bdn honest-traces-v2 \
  bash -c "dotnet add package BenchmarkDotNet >/dev/null && dotnet run -c Release --nologo" > "$OUT" 2>&1
