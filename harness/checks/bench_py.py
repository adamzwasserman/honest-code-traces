"""pyperf checks over the scenario functions in ../python/harness_v2.py.

pyperf calibrates the loop count, spawns worker processes and reports
statistics. Each call runs one scenario on one of 256 inputs. Run by
run-checks.sh, which passes the harness folder in HARNESS_DIR.
"""
import os
import sys

import pyperf

sys.path.insert(0, os.environ["HARNESS_DIR"])
import harness_v2 as h  # noqa: E402

inputs = [[("Widget", 29.99 + v * 0.001), ("Gadget", 39.99), ("Doohickey", 19.99)]
          for v in range(256)]
tax_rates = {"NY": 0.08, "CA": 0.0725}
coupons = {"SAVE10": 0.10}
state = [0]


def dishonest_full():
    state[0] += 1
    return h.dishonest_scenario(inputs[state[0] & 255], True)


def dishonest_no_timestamp():
    state[0] += 1
    return h.dishonest_scenario(inputs[state[0] & 255], False)


def honest():
    state[0] += 1
    return h.honest_scenario(inputs[state[0] & 255], "NY", tax_rates, coupons)


runner = pyperf.Runner()
runner.bench_func("dishonest_full", dishonest_full)
runner.bench_func("dishonest_no_timestamp", dishonest_no_timestamp)
runner.bench_func("honest", honest)
