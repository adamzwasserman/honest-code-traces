# Raw element access in CPython: the language's own loop reads each element
# and does nothing else. The empty loop has the same shape without the read.
import array, json, statistics, time

def timed(n, f, reps):
    f()
    per = []
    for _ in range(reps):
        t0 = time.perf_counter_ns()
        f()
        per.append((time.perf_counter_ns() - t0) / n)
    return statistics.median(per), min(per), max(per)

def report(idiom, n, loop, empty):
    m, lo, hi = timed(n, loop, 3)
    e, _, _ = timed(n, empty, 3)
    print(json.dumps({"language": "python", "idiom": idiom, "n": n,
                      "ns_per_element_median": round(m, 3), "empty_loop": round(e, 3),
                      "net": round(m - e, 3), "min": round(lo, 3), "max": round(hi, 3)}), flush=True)

for n in (1048576, 33554432):
    a = array.array('q', range(n))
    def index_read():
        for i in range(n):
            x = a[i]
    def index_empty():
        for i in range(n):
            pass
    report("a[i] over array('q')", n, index_read, index_empty)
    def iter_read():
        for x in a:
            pass
    def iter_empty():
        for i in range(n):
            pass
    report("for x in array('q')", n, iter_read, iter_empty)
    del a
    lst = list(range(n))
    def index_read_l():
        for i in range(n):
            x = lst[i]
    report("a[i] over list of ints", n, index_read_l, index_empty)
    del lst
