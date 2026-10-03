"""Trace harness v2 (Python): the Honest Code order scenario.

Changes from v1:
  1. The whole scenario is timed once per iteration, not six segments on the
     dishonest side against four on the honest side. Each segment carries one
     clock read, so six against four charged the dishonest side two extra
     clock reads before any code ran.
  2. The primary number is batched: BATCH iterations between two clock reads.
  3. The singletons are built once, before measurement. v1 reset them inside
     the loop, which measures construction, not lookup.
  4. Every result feeds a sink that is printed, and the iteration indexes into
     a table of input variants built before measurement, so nothing is hoisted
     and neither side is charged for building its input.
  5. Three dishonest variants isolate which structural feature costs what.
  6. Percentiles, not a lone median.

Run: python3 harness_v2.py
"""

import json
import time

BATCH = 1000
REPS = 200
WARMUP = 5000
SAMPLES = 5000

ns = time.perf_counter_ns

sink = 0.0

# ─────────────────────────────────────────────
# Dishonest: mutable class, singletons, stamps
# ─────────────────────────────────────────────


class CouponRegistry:
    _instance = None

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def lookup(self, code):
        return 0.10 if code == "SAVE10" else 0.0


class TaxService:
    _instance = None

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def calculate(self, region, taxable):
        return taxable * (0.08 if region == "NY" else 0.0725)


class Order:
    __slots__ = ("_items", "_total", "_discount", "_tax", "_coupon_code",
                 "_updated_at", "_stamp")

    def __init__(self, stamp):
        self._items = []
        self._total = 0.0
        self._discount = 0.0
        self._tax = 0.0
        self._coupon_code = ""
        self._updated_at = 0
        self._stamp = stamp

    def add_item(self, item):
        self._items.append(item)
        self._recalculate_total()
        self._recalculate_discount()
        self._recalculate_tax()
        if self._stamp:
            self._updated_at = ns()

    def apply_coupon(self, code):
        self._coupon_code = code
        self._recalculate_discount()
        self._recalculate_tax()
        if self._stamp:
            self._updated_at = ns()

    def _recalculate_total(self):
        total = 0.0
        for i in self._items:
            total += i[1]
        self._total = total

    def _recalculate_discount(self):
        registry = CouponRegistry.get_instance()
        self._discount = self._total * registry.lookup(self._coupon_code)

    def _recalculate_tax(self):
        svc = TaxService.get_instance()
        self._tax = svc.calculate("NY", self._total - self._discount)

    def grand_total(self):
        return self._total - self._discount + self._tax


def dishonest_scenario(items, stamp):
    order = Order(stamp)
    for it in items:
        order.add_item(it)
    order.apply_coupon("SAVE10")
    return order.grand_total()


# ─────────────────────────────────────────────
# Honest: pure functions, flat data
# ─────────────────────────────────────────────


def calculate_order(items, region, tax_rates):
    total = 0.0
    for i in items:
        total += i[1]
    tax = total * tax_rates.get(region, 0.0)
    return (total, tax, total + tax)


def apply_coupon(order, code, coupons):
    total, tax, subtotal = order
    discount = total * coupons.get(code, 0.0)
    return (total, tax, subtotal, discount, subtotal - discount)


def honest_scenario(items, region, tax_rates, coupons):
    result = calculate_order(items, region, tax_rates)
    final = apply_coupon(result, "SAVE10", coupons)
    return final[4]


# ─────────────────────────────────────────────
# Measurement
# ─────────────────────────────────────────────


def pct(values, p):
    s = sorted(values)
    return s[int(p * (len(s) - 1))]


def distribution(values):
    return {
        "p05": pct(values, 0.05), "p25": pct(values, 0.25),
        "p50": pct(values, 0.50), "p75": pct(values, 0.75),
        "p95": pct(values, 0.95),
    }


def batched(f):
    global sink
    out = []
    for _ in range(REPS):
        t0 = ns()
        for i in range(BATCH):
            sink += f(i)
        out.append((ns() - t0) // BATCH)
    return pct(out, 0.50)


def per_iteration(f):
    global sink
    s = []
    for i in range(SAMPLES):
        t0 = ns()
        sink += f(i)
        s.append(ns() - t0)
    return distribution(s)


def clock_floor():
    s = []
    for _ in range(SAMPLES):
        t0 = ns()
        t1 = ns()
        s.append(t1 - t0)
    return pct(s, 0.50)


def main():
    global sink
    # Input variants are built once, before measurement, so neither side is
    # charged for constructing them.
    inputs = [[("Widget", 29.99 + v * 0.001), ("Gadget", 39.99),
               ("Doohickey", 19.99)] for v in range(256)]
    tax_rates = {"NY": 0.08, "CA": 0.0725}
    coupons = {"SAVE10": 0.10}
    region = "NY"

    # Build the singletons once, before measurement.
    CouponRegistry.get_instance()
    TaxService.get_instance()

    dis_full = lambda i: dishonest_scenario(inputs[i & 255], True)
    dis_nostamp = lambda i: dishonest_scenario(inputs[i & 255], False)
    hon = lambda i: honest_scenario(inputs[i & 255], region, tax_rates, coupons)

    for i in range(WARMUP):
        sink += dis_full(i)
        sink += dis_nostamp(i)
        sink += hon(i)

    floor = clock_floor()
    b_full = batched(dis_full)
    b_nostamp = batched(dis_nostamp)
    b_honest = batched(hon)
    d_full = per_iteration(dis_full)
    d_honest = per_iteration(hon)

    print(json.dumps({
        "language": "python",
        "method": f"whole scenario, {BATCH} iterations between two clock reads, "
                  f"median of {REPS} batches",
        "warmup_iterations": WARMUP,
        "clock_read_pair_ns": floor,
        "batched_ns_per_iteration": {
            "dishonest_full": b_full,
            "dishonest_no_timestamp": b_nostamp,
            "honest": b_honest,
        },
        "ratios": {
            "full_over_honest": round(b_full / b_honest, 2),
            "no_timestamp_over_honest": round(b_nostamp / b_honest, 2),
        },
        "per_iteration_ns": {
            "note": "each sample includes one clock_read_pair_ns of instrument cost",
            "dishonest_full": d_full,
            "honest": d_honest,
        },
        "sink": round(sink, 3),
    }, indent=2))


if __name__ == "__main__":
    main()
