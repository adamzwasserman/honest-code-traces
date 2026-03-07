# Trace Harnesses

Per-language instrumentation harnesses that capture real nanosecond timing for the six crime operations and four rescue operations used across all chapter demos.

## What They Measure

### Crime operations (class-based, dishonest code)

| Operation | What it captures |
|-----------|-----------------|
| `call`    | Method dispatch overhead (vtable, prototype chain, etc.) |
| `field`   | Mutable field write (heap allocation, GC pressure) |
| `calc`    | Computation within a method (stream/reduce over collection) |
| `single`  | Singleton/global lookup |
| `cache`   | Cache check (hash table, concurrent map) |
| `time`    | Timestamp capture (system call overhead) |

### Rescue operations (function-based, honest code)

| Operation | What it captures |
|-----------|-----------------|
| `call`    | Function call overhead |
| `arg`     | Argument passing |
| `calc`    | Pure computation (same math, no dispatch) |
| `ret`     | Return value construction |

## Output Format

Each harness outputs JSON:

```json
{
  "language": "java",
  "machine": "Apple M2 Pro, 16GB",
  "os": "macOS 15.3",
  "runtime": "OpenJDK 21.0.2",
  "runs": 1000,
  "crime": {
    "call": 1.2,
    "field": 23.4,
    "calc": 35.7,
    "single": 58.9,
    "cache": 44.1,
    "time": 11.3
  },
  "rescue": {
    "call": 0.9,
    "arg": 0.4,
    "calc": 4.8,
    "ret": 0.7
  }
}
```

Values are median nanoseconds per operation across N runs. These feed directly into the `makeCrimeTrace()` and `makeRescueTrace()` functions in the landing page animation engine.

## Running

```bash
# Java
cd java && javac Harness.java && java Harness

# Python
cd python && python harness.py

# TypeScript (Node)
cd typescript && npx tsx harness.ts

# Go
cd go && go run harness.go
```
