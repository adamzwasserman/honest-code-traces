"""Chapter 4 demo: the same three operations in all six orders.

The class is ordinary order-dependent code: each method reads the fields the
others have already written. The functions are pure: each takes everything it
needs as arguments, so the order in which they run changes nothing.

Run: uv run python harness/demos/order_dependence.py
"""

from itertools import permutations

PRICE = 89.97
COUPON_RATE = 0.10
TAX_RATE = 0.08


class Order:
    def __init__(self):
        self.items = []
        self.total = 0.0
        self.discount = 0.0
        self.tax = 0.0

    def add_item(self, price):
        self.items.append(price)
        self.total = sum(self.items)

    def apply_coupon(self, rate):
        self.discount = self.total * rate

    def calc_tax(self, rate):
        self.tax = (self.total - self.discount) * rate

    def grand_total(self):
        return self.total - self.discount + self.tax


def class_result(order_of_calls):
    order = Order()
    calls = {
        "addItem": lambda: order.add_item(PRICE),
        "applyCoupon": lambda: order.apply_coupon(COUPON_RATE),
        "calcTax": lambda: order.calc_tax(TAX_RATE),
    }
    for name in order_of_calls:
        calls[name]()
    return order.grand_total()


def calc_total(items):
    return sum(items)


def apply_coupon(items, rate):
    return calc_total(items) * rate


def calc_tax(items, coupon_rate, tax_rate):
    return (calc_total(items) - apply_coupon(items, coupon_rate)) * tax_rate


def function_result(order_of_calls):
    items = [PRICE]
    results = {}
    calls = {
        "calc_total": lambda: calc_total(items),
        "apply_coupon": lambda: apply_coupon(items, COUPON_RATE),
        "calc_tax": lambda: calc_tax(items, COUPON_RATE, TAX_RATE),
    }
    for name in order_of_calls:
        results[name] = calls[name]()
    return results["calc_total"] - results["apply_coupon"] + results["calc_tax"]


def main():
    correct = class_result(("addItem", "applyCoupon", "calcTax"))
    class_orders = list(permutations(("addItem", "applyCoupon", "calcTax")))
    function_orders = list(permutations(("calc_total", "apply_coupon", "calc_tax")))
    class_matches = 0
    function_matches = 0
    print(f"{'class methods':<42}{'total':>8}   {'pure functions':<42}{'total':>8}")
    for class_order, function_order in zip(class_orders, function_orders):
        c = class_result(class_order)
        f = function_result(function_order)
        class_matches += abs(c - correct) < 1e-9
        function_matches += abs(f - correct) < 1e-9
        print(f"{' -> '.join(class_order):<42}{c:>8.2f}   {' -> '.join(function_order):<42}{f:>8.2f}")
    print(f"\nclass methods matching the correct total: {class_matches}/6")
    print(f"pure functions matching the correct total: {function_matches}/6")
    assert class_matches == 1 and function_matches == 6


main()
