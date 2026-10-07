# Honest Code Traces

Instrumented source code and execution traces for every demo on [honestcode.software](https://honestcode.software).

This is the companion repository for the book [*Honest Code: Stop Fighting the Machine*](https://adamzwasserman.gumroad.com/l/honest-code-ebook) by Adam Zachary Wasserman. The book teaches you to replace mutable state, singletons, and inheritance with pure functions, flat data, and composition. The demos on the companion site visualize those differences in real nanoseconds. This repo is how we get those numbers.

## What This Measures

Every demo on honestcode.software shows two versions of the same code side by side: the "crime scene" (classes, mutation, singletons) and the "rescue" (pure functions, flat data). The animation timing is proportional to real nanosecond costs captured from instrumented runs.

We measure six operations on the crime side and four on the rescue side:

**Crime (class-based, dishonest code):**

| Operation | What it captures |
|-----------|-----------------|
| `call`    | Method dispatch (vtable, prototype chain, interface dispatch) |
| `field`   | Mutable field write (heap allocation, GC pressure) |
| `calc`    | Computation within a method (stream/reduce over collection) |
| `single`  | Singleton/global lookup |
| `cache`   | Cache check (hash table, concurrent map) |
| `time`    | Timestamp capture (system call overhead) |

**Rescue (function-based, honest code):**

| Operation | What it captures |
|-----------|-----------------|
| `call`    | Function call overhead |
| `arg`     | Argument passing |
| `calc`    | Pure computation (same math, no dispatch) |
| `ret`     | Return value construction |

## Quick Start (Docker)

Run all four harnesses and get JSON results:

```bash
docker build -t honest-traces .
docker run --rm -v $(pwd)/results:/traces/results honest-traces
```

Results land in `results/`:

```
results/java.json
results/python.json
results/typescript.json
results/go.json
```

## Quick Start (Local)

Run any harness individually:

```bash
# Java (requires JDK 21+)
cd harness/java && javac Harness.java && java Harness

# Python (requires 3.12+)
cd harness/python && python harness.py

# TypeScript (requires Node 20+ and tsx)
cd harness/typescript && npx tsx harness.ts

# Go (requires 1.21+)
cd harness/go && go run harness.go
```

Each prints JSON to stdout with median nanosecond costs per operation.

## Output Format

```json
{
  "language": "java",
  "os": "Mac OS X 15.3.2",
  "runtime": "OpenJDK 64-Bit Server VM 25.0.2",
  "runs": 1000,
  "crime": {
    "call": 292,
    "field": 42,
    "calc": 2458,
    "single": 41,
    "cache": 83,
    "time": 250
  },
  "rescue": {
    "call": 41,
    "arg": 42,
    "calc": 125,
    "ret": 292
  }
}
```

These values feed directly into the `makeCrimeTrace()` and `makeRescueTrace()` functions in the [landing page animation engine](https://honestcode.software).

## Methodology

Each harness:

1. Implements the same Order scenario in its native idiom (mutable class for crime, pure functions for rescue)
2. Warms up for 200 iterations (JIT compilation, cache priming)
3. Runs 1000 measured iterations
4. Reports the median nanosecond cost per operation

The crime scene uses real singletons, real mutable state, and real collection operations. The rescue uses real pure functions with immutable return values. Nothing is faked.

## Baseline Results

Captured in Docker on Apple M2 Pro / macOS 15.3 / arm64:

| Operation | Java | TypeScript | Go | Python |
|-----------|------|------------|-----|--------|
| **Crime** | | | | |
| call | 167 | 125 | 67 | 42 |
| field | 42 | 84 | 0 | 42 |
| calc | 2416 | 208 | 3 | 250 |
| single | 41 | 83 | 24 | 83 |
| cache | 42 | 125 | 1 | 83 |
| time | 333 | 125 | 60 | 83 |
| **Rescue** | | | | |
| call | 42 | 83 | 59 | 42 |
| arg | 42 | 83 | 0 | 42 |
| calc | 542 | 250 | 10 | 333 |
| ret | 292 | 167 | 11 | 209 |

Raw JSON in `harness/baselines/`.

## Repository Structure

```
harness/
  java/Harness.java         # JVM: vtable dispatch, stream API, synchronized singletons
  typescript/harness.ts      # V8: prototype chain, hidden classes, Date.now()
  go/harness.go             # Go: interface dispatch, sync.Once, struct copying
  python/harness.py         # CPython: LOAD_ATTR bytecodes, GIL, __dict__ lookups
  baselines/                # Captured results (JSON) from Docker runs
  README.md                 # Detailed operation descriptions

ch02-classes-considered-harmful/
  crime/Order.java          # The mutable Order class from the book
  rescue/calculate_order.py # Pure function equivalent
  hero-demo/
    generate_traces.py      # Scales baselines to 12 languages for the landing page
    traces/{lang}-crime.json
    traces/{lang}-rescue.json

ch01-all-languages-are-good/    # (coming soon)
ch03-data-is-just-data/         # (coming soon)
...
ch13-the-monday-morning-chapter/
```

## Corrections

The first version of the harnesses measured the wrong thing. We got it wrong in four ways, and all eleven numbers changed when we fixed them.

- The old harnesses timed the dishonest path in six pieces and the honest path in four. Each piece sits between two clock reads, so the dishonest side paid for two more pairs of clock reads than the honest side did. On the benchmark machine a pair of clock reads costs 28 nanoseconds in C++ and 60 in Python, against totals of a few hundred.
- The honest "call" row had two clock reads back to back and no function call between them.
- `CouponRegistry::reset()` and `TaxService::reset()` ran right before the timed singleton section, so every pass paid to build two objects.
- Some labels did not match the code under them.

The rewritten harnesses time the whole scenario once per pass, build the singletons before timing starts, and report batched numbers with percentiles. On 4 October 2026 we also rebuilt the dishonest class so that its methods must run in order. The corrected ratios came out higher than the old ones. That is not why we changed the method. We changed it because the old numbers did not measure what we said they did. The earlier class measurements stay in `measured/cascading-class`, and the commit `240167e` records the change in full.

## The Book

[*Honest Code*](https://honestcode.software) covers 13 chapters on replacing dishonest patterns with honest ones. Every chapter has a crime scene and a rescue, with interactive demos on the companion site. A [free sampler](https://adamzwasserman.gumroad.com/l/honest-code-sampler) is available with the first two chapters and the cheat sheet.

## License

MIT. See [LICENSE](LICENSE).
