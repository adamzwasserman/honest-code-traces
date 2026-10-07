# Memory layout demonstration

This is a demonstration of a well-known effect, not a study. How data sits in memory decides how fast code runs once the data outgrows the processor caches, and every computer architecture text covers it. Ulrich Drepper's paper "What Every Programmer Should Know About Memory" is the standard reference. The point here is to show the size of the effect on one real machine, in three popular languages, with code anyone can rerun.

The three programs sum one field across N records in five layouts: a contiguous array of structs, a struct of arrays, heap objects visited in the order they were allocated, the same objects visited in random order, and a linked list at random addresses. They run at four sizes. `run-layout.sh` runs all three on one pinned core, and the raw results are in `measured/layout`.

On the rented eight-core server we use for every other measurement, visiting heap objects in random order took this many times longer than a contiguous array:

| Data size | C++ | Java | Go |
|---|---|---|---|
| 16 KB | 1.2 | 1.3 | 0.9 |
| 1 MB | 2.4 | 2.4 | 2.3 |
| 32 MB | 7.2 | 4.0 | 4.3 |
| 1 GB | 15.8 | 18.7 | 5.6 |

A linked list at random addresses took 63 to 91 times longer at 1 GB. At 16 KB the data fits in the first cache level, and the layout does not matter. Java with a contiguous array visited 1 GB of records at 3.2 ns each, and C++ with scattered objects took 56 ns each, so layout moved the cost more than the language did.

The server is a virtual machine that hides its real cache layout, and each timing is a median of five repetitions from one hand-written loop. Treat the figures as a demonstration of the effect's size, not as a precise measurement of any one machine.

## Slower languages with a contiguous array

`others/` sums a contiguous array of N integers in Python, Ruby, PHP and Node on the same pinned core, once with the language's own loop and once with its built-in sum, which is a loop written in C. The comparison point is C++ visiting scattered heap objects in random order: 23.6 ns per element at 1,048,576 elements and 56.3 ns at 33,554,432.

| Language and idiom | 1M elements | 33.5M elements |
|---|---|---|
| Ruby, `Array#sum` | 1.6 ns | 1.7 ns |
| Ruby, `each` loop | 76.5 ns | 81.3 ns |
| Python, `sum(array('q'))` | 15.0 ns | 15.1 ns |
| Python, `for` loop over an array | 55.0 ns | 56.2 ns |
| Python, `for` loop over a list | 44.5 ns | 44.8 ns |
| PHP, `array_sum` | 3.4 ns | 3.0 ns |
| PHP, `foreach` over a packed array | 14.4 ns | 13.3 ns |
| Node, loop over a `Float64Array` | 1.5 ns | 1.6 ns |

With a built-in sum, even Ruby beats scattered C++ by 15 times at 1M elements and 34 times at 33.5M. With only its own loop, PHP beats scattered C++ at both sizes, Python beats it at 33.5M by 1.3 times with a list, and Ruby does not beat it at either size.

## Raw element access, with no arithmetic

The sum results above include each language's cost of adding. `others/raw.*` and `others/raw.cpp` time only the read: each loop reads one element and does nothing with it, and an empty loop of the same shape is timed beside it so the loop overhead can be subtracted. The comparison point is again C++ reading scattered heap objects, which costs 22.3 ns per element at 1M elements and 42.6 ns at 33.5M, against 0.4 and 0.9 ns for a contiguous array.

| Language and idiom | 1M loop total | 33.5M loop total | Empty loop | Net read |
|---|---|---|---|---|
| Node, `Float64Array` loop | 1.4 ns | 1.6 ns | 1.2 ns | 0.3 to 0.4 ns |
| PHP, `foreach` over a packed array | 7.5 ns | 7.7 ns | 4.2 to 4.5 ns | 3.2 to 3.4 ns |
| PHP, `$a[$i]` in a `for` loop | 11.9 ns | 12.5 ns | 4.2 to 4.6 ns | 7.7 to 8.0 ns |
| Python, `for x in array('q')` | 20.8 ns | 21.2 ns | 17.9 to 19.4 ns | 1.9 to 2.9 ns |
| Python, `a[i]` over a list | 36.8 ns | 34.3 ns | 18.6 to 18.7 ns | 15.6 to 18.3 ns |
| Python, `a[i]` over an array | 55.8 ns | 54.2 ns | 18.9 to 19.4 ns | 34.8 to 36.9 ns |
| Ruby, `a[i]` in a `while` loop | 31.0 ns | 32.5 ns | 20.7 to 20.9 ns | 10.1 to 11.8 ns |
| Ruby, `Array#each` | 63.5 ns | 63.7 ns | 57.1 to 58.2 ns | 5.5 to 6.4 ns |

With each language's own loop and a contiguous array, Node, PHP and Python's plain iteration beat scattered C++ at both sizes (Python only by 1.07 times at 1M). Ruby's `while` loop beats it at 33.5M by 1.3 times and loses at 1M. Ruby's `each` loses at both sizes. In the slow languages the loop overhead is about the size of C++'s cache-miss penalty, 18 to 58 ns per iteration against 22 to 43 ns, and the net cost of reading a contiguous array is a small part of it.
