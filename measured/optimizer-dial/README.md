# Optimizer dial

The same program at different optimizer settings, from `harness/dial/run-dial.sh`. The dishonest figure is the run without timestamp writes.

`java-*.json` and `java-*.log` are JMH runs of the harness class, three forks of five one-second iterations each: `interpreter` is `-Xint`, `tier1` is `-XX:TieredStopAtLevel=1` (first compiler only), `tier2` is `-XX:-TieredCompilation` (second compiler only) and `default` is the stock tiered JIT. `java-timeline.log` is one fork of forty 100 ms iterations from a cold start. `cpp-O0.json` and `cpp-O2.json` are Google Benchmark runs of the C++ harness at those optimization levels. `php-*-N.json` are three runs of the PHP harness for each mode: no JIT and no opcode cache, the opcode cache only, the tracing JIT and the function JIT.

`inlining/inlining.log` is HotSpot's own compile log (`-XX:+PrintCompilation -XX:+PrintInlining`) for the order scenario under JMH. The tier 4 compile of `JavaBench::dishonestNoTimestamp` (id 625) inlines `HarnessV2::dishonestScenario` and every Order method beneath it: `Order::<init>`, `addItem`, `applyCoupon`, `calcTotal`, `calcDiscount` (with `CouponRegistry::getInstance` and `lookup`), `calcTax` (with `TaxService::getInstance` and `calculate`) and `grandTotal`. `inlining/gc-on.log` and `inlining/gc-off.log` are JMH `-prof gc` runs with escape analysis on and off: dishonest allocates about 0 bytes per call with it on and 176 with it off, honest allocates 56 bytes with it on and 200 with it off.

`python-jit0.json` and `python-jit1.json` are pyperf runs of the Python harness (CPython 3.14.4, Ubuntu build with the experimental JIT compiled in but off by default), ten processes of five values each, with `PYTHON_JIT=0` and `PYTHON_JIT=1`. Medians: class code without timestamps 1,531 ns off and 1,570 ns on; plain functions 708 ns off and 662 ns on.

`python-pypy.json` is the same pyperf harness under PyPy 7.3.20 (Python 3.11.13, Ubuntu `pypy3` package), ten processes of five values each. Medians: class code without timestamps 270 ns (spread 202 to 467), plain functions 135 ns (spread 126 to 168).

`cpp-O1.json` and `cpp-O3.json` repeat the C++ run at -O1 and -O3 with Google Benchmark, five repetitions. Medians for class code without timestamps and plain functions: -O0 2,027 and 562 ns, -O1 162 and 37, -O2 106 and 25, -O3 120 and 19.
