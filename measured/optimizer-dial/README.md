# Optimizer dial

The same program at different optimizer settings, from `harness/dial/run-dial.sh`. The dishonest figure is the run without timestamp writes.

`java-*.json` and `java-*.log` are JMH runs of the harness class, three forks of five one-second iterations each: `interpreter` is `-Xint`, `tier1` is `-XX:TieredStopAtLevel=1` (first compiler only), `tier2` is `-XX:-TieredCompilation` (second compiler only) and `default` is the stock tiered JIT. `java-timeline.log` is one fork of forty 100 ms iterations from a cold start. `cpp-O0.json` and `cpp-O2.json` are Google Benchmark runs of the C++ harness at those optimization levels. `php-*-N.json` are three runs of the PHP harness for each mode: no JIT and no opcode cache, the opcode cache only, the tracing JIT and the function JIT.
