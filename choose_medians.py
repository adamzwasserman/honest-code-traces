"""Pick the dishonest and honest figures for each language from every
measurement we hold, and write harness/medians_v2.json.

The dishonest figure is the run without the timestamp writes, so the
timestamps carry no time. For each language we hold several measurements,
each a (dishonest, honest) pair from one method: our hand-written loop and
one or more benchmarking tools. The pair with the smallest ratio wins.

Run: uv run python choose_medians.py
"""

import glob
import json
import re
import statistics as st
from pathlib import Path

HERE = Path(__file__).parent
M = HERE / "measured"
OUT = HERE / "harness" / "medians_v2.json"


def loop_pair(lang, pattern=None):
    files = glob.glob(str(M / "runs" / "run*" / f"{lang}.json"))
    if pattern:
        files = glob.glob(pattern)
    values = [json.load(open(f))["batched_ns_per_iteration"] for f in files]
    return (
        st.median(v["dishonest_no_timestamp"] for v in values),
        st.median(v["honest"] for v in values),
    )


def jmh_pair(path):
    scores = {b["benchmark"].split(".")[-1]: b["primaryMetric"]["score"] for b in json.load(open(path))}
    return scores["dishonestNoTimestamp"], scores["honest"]


def gbench_cpp():
    by = {}
    for b in json.load(open(M / "googlebenchmark-cpp.json"))["benchmarks"]:
        if b.get("run_type") == "iteration":
            by.setdefault(b["name"].split("/")[0], []).append(b["cpu_time"])
    return st.median(by["DishonestNoTimestamp"]), st.median(by["Honest"])


def go_testing():
    by = {}
    for line in open(M / "go-testing-bench.txt"):
        m = re.match(r"Benchmark(\w+?)(-\d+)?\s+\d+\s+([\d.]+) ns/op", line)
        if m:
            by.setdefault(m.group(1), []).append(float(m.group(3)))
    return st.median(by["DishonestNoTimestamp"]), st.median(by["Honest"])


def mitata_ts():
    stats = {b["alias"]: b["runs"][0]["stats"]["p50"] for b in json.load(open(M / "mitata-typescript.json"))["benchmarks"]}
    return stats["dishonest_no_timestamp"], stats["honest"]


def swift_pkg():
    text = open(M / "swift-package-benchmark.txt").read()
    out = {}
    for name in ("dishonest_no_timestamp", "honest"):
        m = re.search(name + r"\n.*?\n.*?\n.*?\n│ Time \(wall clock\) \(ns\) \* │\s+(\d+) │\s+(\d+) │\s+(\d+) │", text, re.S)
        out[name] = int(m.group(3))
    return out["dishonest_no_timestamp"], out["honest"]


def dart_harness(path):
    by = {}
    for line in open(path):
        p = line.split()
        if len(p) == 2:
            by.setdefault(p[0], []).append(float(p[1]))
    return st.median(by["dishonest_no_timestamp"]), st.median(by["honest"])


def pyperf_py():
    out = {}
    for b in json.load(open(M / "python-pyperf.json"))["benchmarks"]:
        vals = [x for r in b["runs"] for x in r.get("values", [])]
        out[b["metadata"]["name"]] = st.median(vals) * 1e9
    return out["dishonest_no_timestamp"], out["honest"]


def ips_ruby():
    by = {}
    for line in open(M / "ruby-benchmark-ips.txt"):
        m = re.match(r"\s*(dishonest_no_timestamp|honest)\s+([\d.]+)([kM]?)\s+\(", line)
        if m:
            by.setdefault(m.group(1), []).append(1e9 / (float(m.group(2)) * {"": 1, "k": 1e3, "M": 1e6}[m.group(3)]))
    return st.median(by["dishonest_no_timestamp"]), st.median(by["honest"])


def phpbench_php():
    out = {}
    for line in open(M / "php-phpbench.txt"):
        m = re.search(r"bench(DishonestNoTimestamp|Honest)\s+\|\s+\|\s+\d+\s+\|\s+\d+\s+\|\s+[\d.]+kb\s+\|\s+([\d.]+)μs", line)
        if m:
            out[m.group(1)] = float(m.group(2)) * 1000
    return out["DishonestNoTimestamp"], out["Honest"]


def bdn_csharp():
    out = {}
    for line in open(M / "benchmarkdotnet.txt"):
        cells = [c.strip() for c in line.split("|")]
        if len(cells) > 8 and cells[1] in ("DishonestNoTimestamp", "Honest"):
            out[(cells[1], cells[2])] = float(cells[7].split()[0])
    return {
        "BenchmarkDotNet, tiered compilation on": (out[("DishonestNoTimestamp", "tiered-default")], out[("Honest", "tiered-default")]),
        "BenchmarkDotNet, tiered compilation off": (out[("DishonestNoTimestamp", "tiered-off")], out[("Honest", "tiered-off")]),
    }


CANDIDATES = {
    "python": {"hand-written loop": loop_pair("python"), "pyperf": pyperf_py()},
    "typescript": {"hand-written loop": loop_pair("typescript"), "mitata": mitata_ts()},
    "ruby": {"hand-written loop": loop_pair("ruby"), "benchmark-ips": ips_ruby()},
    "php": {"hand-written loop": loop_pair("php"), "PHPBench": phpbench_php()},
    "go": {"hand-written loop": loop_pair("go"), "go test -bench with b.Loop": go_testing()},
    "java": {
        "hand-written loop": loop_pair("java"),
        "JMH, tiered compilation on": jmh_pair(M / "jmh-java.json"),
        "JMH, tiered compilation off": jmh_pair(M / "jmh-java-tiered-off.json"),
    },
    "kotlin": {
        "hand-written loop": loop_pair("kotlin", str(M / "runs" / "kotlin_after_fix" / "*.json")),
        "JMH, tiered compilation on": jmh_pair(M / "jmh-kotlin.json"),
        "JMH, tiered compilation off": jmh_pair(M / "jmh-kotlin-tiered-off.json"),
    },
    "csharp": {"hand-written loop": loop_pair("csharp"), **bdn_csharp()},
    "swift": {"hand-written loop": loop_pair("swift"), "Benchmark package, 1,000 passes per sample": swift_pkg()},
    "dart": {"hand-written loop": loop_pair("dart"), "benchmark_harness": dart_harness(M / "dart-benchmark-harness.txt")},
    "dart_aot": {"hand-written loop": loop_pair("dart_aot"), "benchmark_harness": dart_harness(M / "dart-aot-benchmark-harness.txt")},
    "cpp": {"hand-written loop": loop_pair("cpp"), "Google Benchmark": gbench_cpp()},
}


def main():
    out = {}
    print(f"{'language':<10}{'dishonest':>10}{'honest':>9}{'ratio':>7}   method")
    for lang, options in CANDIDATES.items():
        method, (dishonest, honest) = min(options.items(), key=lambda kv: kv[1][0] / kv[1][1])
        out[lang] = {
            "dishonest_ns": round(dishonest, 1),
            "honest_ns": round(honest, 1),
            "source": method,
            "candidates": {k: {"dishonest_ns": round(d, 1), "honest_ns": round(h, 1), "ratio": round(d / h, 2)} for k, (d, h) in options.items()},
        }
        print(f"{lang:<10}{dishonest:>10.1f}{honest:>9.1f}{dishonest / honest:>7.2f}   {method}")
    OUT.write_text(json.dumps(out, indent=1))


main()
