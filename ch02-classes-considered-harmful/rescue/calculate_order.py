"""
The Honest Order — pure functions, flat data.

No classes. No mutable state. No singletons. Each function takes data in
and returns data out. The call order doesn't matter because nothing is
mutated between calls.

Instrumented with time.perf_counter_ns() to capture per-operation costs.
"""
import json
import time


def calculate_order(
    items: list[dict], region: str, tax_rates: dict[str, float]
) -> dict:
    """Calculate order totals from items. Pure function — no side effects."""
    t0 = time.perf_counter_ns()
    total = sum(item["price"] for item in items)
    t1 = time.perf_counter_ns()
    tax = total * tax_rates.get(region, 0)
    t2 = time.perf_counter_ns()
    subtotal = total + tax
    t3 = time.perf_counter_ns()

    return {
        "total": round(total, 2),
        "tax": round(tax, 2),
        "subtotal": round(subtotal, 2),
        "_trace": [t1 - t0, t2 - t1, t3 - t2],
    }


def apply_coupon(order: dict, code: str, coupons: dict[str, float]) -> dict:
    """Apply a coupon to an order result. Returns a new dict — no mutation."""
    t0 = time.perf_counter_ns()
    rate = coupons.get(code, 0)
    discount = order["total"] * rate
    t1 = time.perf_counter_ns()
    grand_total = order["subtotal"] - discount
    t2 = time.perf_counter_ns()

    return {
        **order,
        "coupon_code": code,
        "discount": round(discount, 2),
        "grand_total": round(grand_total, 2),
        "_trace": order["_trace"] + [t1 - t0, t2 - t1],
    }


def main():
    items = [
        {"name": "Widget", "price": 29.99},
        {"name": "Gadget", "price": 39.99},
        {"name": "Doohickey", "price": 19.99},
    ]
    tax_rates = {"NY": 0.08, "CA": 0.0725}
    coupons = {"SAVE10": 0.10}

    result = calculate_order(items, "NY", tax_rates)
    final = apply_coupon(result, "SAVE10", coupons)

    # Output trace
    trace = final.pop("_trace")
    print(json.dumps({"result": final, "trace_ns": trace}, indent=2))


if __name__ == "__main__":
    main()
