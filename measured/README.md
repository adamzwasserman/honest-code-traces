# Measured results

`runs/run1` to `runs/run10` hold the output of `run-all-v2.sh`, one run per folder, 12 files each (Dart appears twice: `dart.json` runs it as it builds, `dart_aot.json` runs the precompiled program). Every run used all eight cores of one rented server, inside the image built from the `Dockerfile` in this repository. The `kotlin.json` files in `run1` to `run10` come from before the Kotlin timing fix in commit `6240b7f`, and `runs/kotlin_after_fix` holds ten runs after it.

`jmh-java.json` and `jmh-kotlin.json` are the JMH output for Java and Kotlin, and `benchmarkdotnet.txt` is the BenchmarkDotNet table for C#. `../harness/medians_v2.json` holds the numbers the website uses: medians of the ten runs for most languages, JMH for Java and Kotlin, and BenchmarkDotNet with tiered compilation off for C#.
