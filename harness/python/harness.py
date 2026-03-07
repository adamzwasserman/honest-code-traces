"""
Trace harness: captures median nanosecond costs for the six crime
operations and four rescue operations used in the honestcode.software
demo engine.

Runs N iterations, discards warmup, reports median per operation.

Usage:
    python harness.py
"""
import json
import platform
import statistics
import sys
import time


WARMUP = 200
RUNS = 1000


# ═══════════════════════════════════════════════
# CRIME SCENE: mutable Order class with singletons
# ═══════════════════════════════════════════════

class CouponRegistry:
    _instance = None

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def lookup(self, code):
        if code == "SAVE10":
            return 0.10
        return 0


class TaxService:
    _instance = None

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def calculate(self, region, taxable):
        return taxable * 0.08


class Order:
    def __init__(self):
        self.items = []
        self.total = 0.0
        self.discount = 0.0
        self.tax = 0.0
        self.coupon_code = None
        self.updated_at = None


def crime_run():
    """Measure individual crime operations. Returns [call, field, calc, single, cache, time]."""
    order = Order()
    items = [
        {"name": "Widget", "price": 29.99},
        {"name": "Gadget", "price": 39.99},
        {"name": "Doohickey", "price": 19.99},
    ]
    ns = time.perf_counter_ns

    # call: method dispatch (simulate addItem call overhead)
    t0 = ns()
    order.items.extend(items)
    call_ns = ns() - t0

    # field: mutable field write
    t0 = ns()
    order.coupon_code = "SAVE10"
    order.discount = 0.0
    field_ns = ns() - t0

    # calc: computation (sum over collection via attribute access)
    t0 = ns()
    order.total = sum(item["price"] for item in order.items)
    calc_ns = ns() - t0

    # single: singleton lookup
    t0 = ns()
    registry = CouponRegistry.get_instance()
    tax_service = TaxService.get_instance()
    single_ns = (ns() - t0) // 2  # per lookup

    # cache: cache check (hash-based lookup)
    t0 = ns()
    rate = registry.lookup(order.coupon_code)
    order.discount = order.total * rate
    cache_ns = ns() - t0

    # time: timestamp capture
    t0 = ns()
    order.updated_at = time.time_ns()
    time_ns = ns() - t0

    # calc tax to prevent dead code elimination
    taxable = order.total - order.discount
    order.tax = tax_service.calculate("NY", taxable)

    return [call_ns, field_ns, calc_ns, single_ns, cache_ns, time_ns]


# ═══════════════════════════════════════════════
# RESCUE: pure functions, flat data
# ═══════════════════════════════════════════════

def calculate_order(items, region, tax_rates):
    total = sum(item["price"] for item in items)
    tax = total * tax_rates.get(region, 0)
    return total, tax, total + tax


def apply_coupon(total, subtotal, code, coupons):
    rate = coupons.get(code, 0)
    discount = total * rate
    return code, discount, subtotal - discount


def rescue_run():
    """Measure individual rescue operations. Returns [call, arg, calc, ret]."""
    items = [
        {"name": "Widget", "price": 29.99},
        {"name": "Gadget", "price": 39.99},
        {"name": "Doohickey", "price": 19.99},
    ]
    tax_rates = {"NY": 0.08, "CA": 0.0725}
    coupons = {"SAVE10": 0.10}
    ns = time.perf_counter_ns

    # call: function call overhead
    t0 = ns()
    t1 = ns()
    call_ns = t1 - t0

    # arg: argument passing
    t0 = ns()
    region = "NY"
    arg_ns = ns() - t0

    # calc: pure computation (returns tuples, not dicts)
    t0 = ns()
    total, tax, subtotal = calculate_order(items, region, tax_rates)
    calc_ns = ns() - t0

    # ret: apply coupon (returns tuple, no allocation beyond that)
    t0 = ns()
    code, discount, grand_total = apply_coupon(total, subtotal, "SAVE10", coupons)
    ret_ns = ns() - t0

    # prevent dead code elimination
    assert grand_total > 0

    return [call_ns, arg_ns, calc_ns, ret_ns]


# ═══════════════════════════════════════════════
# HARNESS: warmup, collect, median
# ═══════════════════════════════════════════════

def main():
    # Warmup
    for _ in range(WARMUP):
        crime_run()
        rescue_run()

    # Collect
    crime_results = [crime_run() for _ in range(RUNS)]
    rescue_results = [rescue_run() for _ in range(RUNS)]

    # Compute medians
    crime_ops = ["call", "field", "calc", "single", "cache", "time"]
    rescue_ops = ["call", "arg", "calc", "ret"]

    crime_medians = {}
    for i, op in enumerate(crime_ops):
        col = [r[i] for r in crime_results]
        crime_medians[op] = statistics.median(col)

    rescue_medians = {}
    for i, op in enumerate(rescue_ops):
        col = [r[i] for r in rescue_results]
        rescue_medians[op] = statistics.median(col)

    output = {
        "language": "python",
        "os": f"{platform.system()} {platform.release()}",
        "runtime": f"CPython {platform.python_version()}",
        "runs": RUNS,
        "crime": crime_medians,
        "rescue": rescue_medians,
    }

    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
