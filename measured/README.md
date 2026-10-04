# Measured results

These are the results for the order-dependent crime scene: an Order class whose calcTotal, calcDiscount and calcTax methods must run in the right order, set against two pure functions. Both return 87.45084 for the sample order. The earlier measurements of a class that recalculated everything after each change are in `cascading-class/`.

`runs/run1` to `runs/run10` hold the output of `run-all-v2.sh`, ten runs of 12 harness outputs each (Dart appears twice: `dart.json` runs it as it builds, `dart_aot.json` runs the precompiled program). Every run used all eight cores of one rented server, inside the image built from the `Dockerfile` in this repository.

The other files are the benchmarking-tool checks, one per language: `googlebenchmark-cpp.json`, `go-testing-bench.txt`, `mitata-typescript.json`, `swift-package-benchmark.txt`, `dart-benchmark-harness.txt`, `dart-aot-benchmark-harness.txt`, `python-pyperf.json`, `ruby-benchmark-ips.txt`, `php-phpbench.txt`, the four `jmh-*.json` files for Java and Kotlin (with tiered compilation on and off) and `benchmarkdotnet.txt` for C#. Swift runs at 1,000 passes per sample, because at one pass the tool added about 120 ns of its own timing cost to every figure.

`../choose_medians.py` reads all of these. For each language it picks the measurement, our loop or a tool, whose dishonest-to-honest ratio is smallest, and writes `../harness/medians_v2.json` with every candidate recorded. The dishonest figure is the run without the timestamp writes. `../run-everything.sh` produces the raw output and `../collect_results.sh` copies it here.
