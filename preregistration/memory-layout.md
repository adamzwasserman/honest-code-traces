# Pre-registration: does contiguous memory change speed by more than 25 percent?

Written on 2026-10-04, before any experiment code was written or run. The commit that adds this file is the timestamp. Changes after the first measurement go in a dated section at the end and never replace the text above it.

## Hypothesis

H1, in Adam Wasserman's words: "That contiguous memory, whether through careful use of the language, or from optimization, has a greater than 25% performance impact on code execution."

## Definitions

Contiguous means the elements the code touches sit at consecutive addresses: an array of values, an array of structs, or a struct of arrays.

Scattered means the code reaches the elements through pointers to separately allocated objects, visited in an order that differs from the order they were allocated, or through a linked list built in random address order.

Impact is the ratio R = time per element when the layout is scattered, divided by time per element when it is contiguous, in the same language, on the same machine, doing the same work. An impact greater than 25 percent means R > 1.25. This is the easier of the two possible readings, since measuring against the slower layout would need R > 1.33.

Careful use of the language is route A: the programmer chooses the layout. Optimization is route B: a compiler or runtime produces the contiguity or removes the allocation, for example by escape analysis, or through the allocation order and compaction that a managed runtime applies.

## Experiments

E1, layout sweep. In C++ at -O2, Java and Go, sum one field across N records of four 8-byte fields. The layouts are L1, an array of structs; L2, a struct of arrays; L3, an array of heap objects allocated in order and visited in order; L4, the same objects visited in a random order; and L5, a linked list whose nodes sit at random addresses. The working-set tiers are about 16 KB, 1 MB, 32 MB and 1 GB. The random orders use a fixed seed.

E2, the optimizer route. Run the order scenario from this repository in Java with escape analysis on and off, and report C++ at -O0 and -O2 from the existing optimizer-dial data. These runs show what happens when the optimizer keeps or removes the allocation. They test route B only in the sense that the allocation stays or goes, and the write-up must say so.

E3, allocation locality in a managed language. In Java, build an array of objects allocated one after another, and build another allocated with garbage interleaved between them. Measure sequential traversal of both.

## Method

Each configuration runs with several forks or repetitions: JMH with 5 forks for Java, Google Benchmark with 5 repetitions for C++, and the Go testing package with 10 counts. The report gives the median and the range for each. The machine is the rented eight-core server used for every other measurement, with the benchmark pinned to one core. Warm-up is long enough for the Java and Go runtimes to settle.

## Decision rules

R is the median ratio for L4 against L1, unless a rule names another layout.

H1 is supported at a tier for a language when R > 1.25 and the ranges of the two layouts do not overlap.

H1 is supported overall when it is supported at both the 32 MB and 1 GB tiers in all three languages, and E2 shows at least one case with R > 1.25.

H1 is refuted for large data when R <= 1.25 at both the 32 MB and 1 GB tiers in two or more of the three languages.

H1 holds only for large data when R > 1.25 appears only at tiers above the first two. In that case the write-up states the smallest tier at which R crosses 1.25 and does not claim the general form of H1.

Any other pattern is reported as mixed, with the full table.

## Known limits

The server is a virtual machine that hides the real cache topology, and other tenants share the host. The measurements come from one machine with one processor family. Hardware prefetching can hide a scattered layout when the access pattern is regular, which is why L3 and L4 are separate. The hypothesis speaks of code execution in general, and these experiments test traversal workloads only, so every other workload is outside the claim they can support or refute. The three-item order scenario cannot test H1, because its data fits in the first cache level.

## Deviation recorded on 2026-10-04, before the full run

The method section named JMH for Java, Google Benchmark for C++ and the Go testing package. Experiment 1 instead uses one hand-written timing loop in all three languages, with the same structure: two warm-up traversals, then five timed repetitions of at least 64 million element visits each, reporting the median and the range. The reason is that the data takes seconds to build at the larger sizes, and the benchmarking tools rebuild it on every call, which is impractical, and one loop makes the three languages directly comparable. Each traversal changes one element so that no compiler can hoist the work out of the loop, and the quick check confirmed that the timings scale with the layout. The cost is that the hand-written loop lacks the extra protection those tools give against dropped work. The decision rules above are unchanged.

## Results for Experiments 1 and 2, recorded on 2026-10-04

Raw data for Experiment 1 is in `measured/layout/*.jsonl`. R is the median time per element for L4 (heap objects visited in random order) divided by L1 (contiguous). In the "separate" column, yes means the ranges of the two layouts do not overlap.

| Tier | C++ R | Java R | Go R |
|---|---|---|---|
| 16 KB | 1.21, separate | 1.32, not separate | 0.86, not separate |
| 1 MB | 2.44, separate | 2.41, separate | 2.33, separate |
| 32 MB | 7.20, separate | 4.00, separate | 4.28, separate |
| 1 GB | 15.75, separate | 18.66, separate | 5.58, separate |

Other layouts against L1: the linked list (L5) is 21 to 22 times slower at 1 MB, 34 to 45 times slower at 32 MB and 63 to 91 times slower at 1 GB in all three languages. Heap objects visited in allocation order (L3) are 1.6 to 2.0 times slower than L1 at 32 MB in C++ and Java, 1.19 times in Go, and 1.7, 1.9 and 1.07 times at 1 GB for C++, Java and Go. Summing one field from the struct of arrays (L2) is 3 to 6 times faster than from the array of structs at 32 MB and above.

Experiment 2, Java order scenario without timestamps, JMH, 3 forks: with escape analysis on the dishonest class took 40.0 ± 4.2 ns and the honest functions 23.7 ± 3.0 ns. With escape analysis off they took 65.2 ± 2.8 ns and 42.0 ± 1.7 ns, so R = 1.63 for the dishonest class and 1.77 for the honest functions, and the ranges do not overlap. C++ at -O0 against -O2 is in `measured/optimizer-dial`.

Applying the decision rules: R is above 1.25 with separate ranges at the 32 MB and 1 GB tiers in all three languages, and Experiment 2 shows two cases above 1.25. H1 is supported for traversal workloads. The effect also appears at the 1 MB tier in all three languages, so it is not limited to the largest sizes. It does not appear at the 16 KB tier, where the data fits in the first cache level: R is 1.21 in C++, 1.32 in Java without separate ranges, and 0.86 in Go. Experiment 3 has not been run, and the decision rules do not depend on it.
