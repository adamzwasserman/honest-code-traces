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
