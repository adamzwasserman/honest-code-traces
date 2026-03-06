# Honest Code Traces

Instrumented source code and execution traces for every demo on [honestcode.software](https://honestcode.software).

## What This Is

Every interactive demo on the Honest Code companion site visualizes the execution difference between "dishonest" code (mutable state, singletons, hidden side effects) and "honest" code (pure functions, flat data, composition). The timing shown is proportional to real nanosecond costs per language runtime.

This repository contains:

- **Source code** for each chapter's crime scene and rescue
- **Execution traces** (JSON) captured from real instrumented runs
- **The trace generator** that scales baseline captures across 12 language runtimes

## Repository Structure

```
ch02-classes-considered-harmful/
  crime/Order.java              # The mutable Order class
  rescue/calculate_order.py     # Pure function equivalent
  hero-demo/
    generate_traces.py          # Scales Java/Python baselines to all 12 languages
    traces/{lang}-crime.json    # Per-language crime traces
    traces/{lang}-rescue.json   # Per-language rescue traces
  traces/
    crime.json                  # Raw Java capture (coming soon)
    rescue.json                 # Raw Python capture (coming soon)
```

Each chapter follows the same `crime/` + `rescue/` + `traces/` pattern.

## Trace JSON Format

Crime traces (class-based, dishonest):

```json
[
  [nanoseconds, [["state_field_id", "value"], ...], singleton_lookup_count],
  ...
]
```

Rescue traces (function-based, honest):

```json
[
  [nanoseconds, [["result_field_id", "value"], ...]],
  ...
]
```

Each entry represents one step in the execution. The visualization engine on honestcode.software replays these traces with timing proportional to the nanosecond values.

## Per-Language Scaling Methodology

The hero demo on the landing page shows the same crime/rescue pattern in 12 languages. Since we can't run identical code across all 12 runtimes, we:

1. **Capture real baselines** in Java (crime) and Python (rescue) with `System.nanoTime()` / `time.perf_counter_ns()`
2. **Scale nanosecond values** using published benchmark ratios for equivalent operations in each language

The scaling factors account for:
- **Method dispatch overhead**: vtable (C++/Java), witness table (Swift), interface dispatch (Go), prototype chain (JS)
- **Field access cost**: direct (C++), managed heap (Java/C#), hash-table property (Python/Ruby/PHP)
- **Singleton/global lookup**: varies from near-zero (compiled, cached) to hundreds of ns (interpreted, GIL)
- **Function call overhead**: inlined (JIT languages) vs interpreted (CPython/YARV/Zend)

Sources for scaling ratios:
- [Computer Language Benchmarks Game](https://benchmarksgame-team.pages.debian.net/benchmarksgame/)
- JMH microbenchmarks for JVM languages
- V8/SpiderMonkey internal benchmarks for JS/TS
- Language-specific profiling documentation

See `ch02/hero-demo/generate_traces.py` for the exact factors and their documentation.

## Hardware

Baseline captures were run on:

- **Machine**: (TBD — fill in when captures are done)
- **OS**: (TBD)
- **Runtimes**: OpenJDK 21, CPython 3.12

## Reproducing Captures

### Chapter 2: Classes Considered Harmful

Crime scene (Java):
```bash
cd ch02-classes-considered-harmful/crime
javac Order.java
java -cp . Order > ../traces/crime.json
```

Rescue (Python):
```bash
cd ch02-classes-considered-harmful/rescue
python calculate_order.py > ../traces/rescue.json
```

Generate all 12 language variants:
```bash
cd ch02-classes-considered-harmful/hero-demo
python generate_traces.py
# Outputs traces/{lang}-crime.json and traces/{lang}-rescue.json
```

## License

MIT — see [LICENSE](LICENSE).
