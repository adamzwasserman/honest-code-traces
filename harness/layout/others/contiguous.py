# Sums N int64 values held in a contiguous array, in CPython, three ways.
import array, json, statistics, sys, time

def run(idiom, n, f, reps):
    f()
    per = []
    for _ in range(reps):
        t0 = time.perf_counter_ns()
        f()
        per.append((time.perf_counter_ns() - t0) / n)
    print(json.dumps({"language": "python", "idiom": idiom, "n": n,
                      "ns_per_element_median": round(statistics.median(per), 3),
                      "min": round(min(per), 3), "max": round(max(per), 3)}), flush=True)

for n in (1048576, 33554432):
    a = array.array('q', range(n))

    def loop():
        s = 0
        for x in a:
            s += x
        return s
    run("for loop over array('q')", n, loop, 3)
    run("sum(array('q'))", n, lambda: sum(a), 5)
    del a
    lst = list(range(n))

    def loop_list():
        s = 0
        for x in lst:
            s += x
        return s
    run("for loop over list of ints", n, loop_list, 3)
    del lst
