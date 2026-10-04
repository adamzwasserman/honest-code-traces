"""
Generate the canonical trace structure for honestcode.software.

Each language has a walk-through of the order-dependent Order class (15 steps)
and of the two pure functions (9 steps). The step widths come from per-operation
costs in harness/baselines/*.json, which the old harness measured. They only set
the proportions between steps: normalize_traces.py then scales each side to the
measured totals in harness/medians_v2.json.

Usage:
    uv run python generate_traces.py
    uv run python normalize_traces.py
"""

import json
from pathlib import Path

BASELINES_DIR = Path(__file__).parent / "harness" / "baselines"
OUTPUT = Path(__file__).parent / "traces" / "landing-page.json"

# 15 crime steps: which baseline operation sets each step's width.
# The class has separate calculation methods, so the caller must call them in
# the right order: addItem, applyCoupon, calcTotal, calcDiscount, calcTax.
CRIME_OPS = [
    "call",    # order.addItem(newItem)
    "field",   # items.add(item)
    "time",    # updatedAt = now()
    "call",    # order.applyCoupon("SAVE10")
    "field",   # couponCode = code
    "time",    # updatedAt = now()
    "call",    # order.calcTotal()
    "calc",    # total = sum of prices
    "call",    # order.calcDiscount()
    "single",  # CouponRegistry singleton
    "cache",   # lookup(couponCode)
    "field",   # discount = total * rate
    "call",    # order.calcTax()
    "single",  # TaxService singleton
    "field",   # tax = svc.calculate(region, taxable)
]

# State mutations shown by each crime step (index -> [[id, value]])
CRIME_MUTATIONS = {
    2:  [["s-updated", "14:23:07.4"]],
    4:  [["s-coupon", '"SAVE10"']],
    5:  [["s-updated", "14:23:07.5"]],
    7:  [["s-total", "89.97"]],
    11: [["s-discount", "9.00"]],
    14: [["s-tax", "6.48"]],
}

# Singleton count per crime step
CRIME_SINGLETONS = {9: 1, 13: 1}

# 9 rescue steps: the discount first, then the tax, as two pure functions
RESCUE_OPS = [
    "call",  # applyCoupon(
    "arg",   # items, "SAVE10", coupons)
    "calc",  # total = sum(...)
    "calc",  # discount = total * rate
    "ret",   # return total, discount, subtotal
    "call",  # calculateTax(
    "arg",   # result, region, taxRates)
    "calc",  # tax = subtotal * rate
    "ret",   # return the grand total
]

# Results displayed by each rescue step
RESCUE_RESULTS = {
    1: [["r-coupon", '"SAVE10"']],
    2: [["r-total", "89.97"]],
    3: [["r-discount", "9.00"]],
    4: [["r-subtotal", "80.97"]],
    7: [["r-tax", "6.48"]],
    8: [["r-grand", "87.45"]],
}

LANG_CODE = {
    "java": {
        "crimeLabel": "☠ Dishonest — Java Order class",
        "rescueLabel": "✦ Honest — Pure functions (Java)",
        "crime": [
            "order.addItem(newItem);",
            "  this.items.add(item);",
            "  this.updatedAt = now();",
            "order.applyCoupon(\"SAVE10\");",
            "  this.couponCode = code;",
            "  this.updatedAt = now();",
            "order.calcTotal();",
            "  this.total = items.stream()...",
            "order.calcDiscount();",
            "  CouponRegistry.getInstance()",
            "    .lookup(couponCode)",
            "  this.discount = total * rate;",
            "order.calcTax();",
            "  TaxService.getInstance()",
            "  this.tax = svc.calculate(region, taxable);"
        ],
        "rescue": [
            "var result = applyCoupon(",
            "    items, \"SAVE10\", coupons);",
            "  var total = items.stream()...",
            "  var discount = total * rate;",
            "  return new Priced(total, discount, ...);",
            "var final = calculateTax(",
            "    result, \"NY\", taxRates);",
            "  var tax = subtotal * taxRates.get(region);",
            "  return new Final(..., tax, grandTotal);"
        ]
    },
    "typescript": {
        "crimeLabel": "☠ Dishonest — TypeScript Order class",
        "rescueLabel": "✦ Honest — Pure functions (TypeScript)",
        "crime": [
            "order.addItem(newItem);",
            "  this.items.push(item);",
            "  this.updatedAt = new Date();",
            "order.applyCoupon(\"SAVE10\");",
            "  this.couponCode = code;",
            "  this.updatedAt = new Date();",
            "order.calcTotal();",
            "  this.total = this.items.reduce(...)",
            "order.calcDiscount();",
            "  CouponRegistry.getInstance()",
            "    .lookup(this.couponCode)",
            "  this.discount = this.total * rate;",
            "order.calcTax();",
            "  TaxService.getInstance()",
            "  this.tax = svc.calculate(region, taxable);"
        ],
        "rescue": [
            "const result = applyCoupon(",
            "    items, \"SAVE10\", coupons);",
            "  const total = items.reduce(...)",
            "  const discount = total * rate;",
            "  return { total, discount, subtotal };",
            "const final = calculateTax(",
            "    result, \"NY\", taxRates);",
            "  const tax = subtotal * taxRates[region];",
            "  return { ...result, tax, grandTotal };"
        ]
    },
    "csharp": {
        "crimeLabel": "☠ Dishonest — C# Order class",
        "rescueLabel": "✦ Honest — Pure functions (C#)",
        "crime": [
            "order.AddItem(newItem);",
            "  _items.Add(item);",
            "  UpdatedAt = DateTime.Now;",
            "order.ApplyCoupon(\"SAVE10\");",
            "  CouponCode = code;",
            "  UpdatedAt = DateTime.Now;",
            "order.CalcTotal();",
            "  Total = _items.Sum(i => i.Price);",
            "order.CalcDiscount();",
            "  CouponRegistry.Instance",
            "    .Lookup(CouponCode)",
            "  Discount = Total * rate;",
            "order.CalcTax();",
            "  TaxService.Instance",
            "  Tax = svc.Calculate(region, taxable);"
        ],
        "rescue": [
            "var result = ApplyCoupon(",
            "    items, \"SAVE10\", coupons);",
            "  var total = items.Sum(i => i.Price);",
            "  var discount = total * rate;",
            "  return new Priced(total, discount, ...);",
            "var final = CalculateTax(",
            "    result, \"NY\", taxRates);",
            "  var tax = subtotal * taxRates[region];",
            "  return new Final(..., tax, grandTotal);"
        ]
    },
    "python": {
        "crimeLabel": "☠ Dishonest — Python Order class",
        "rescueLabel": "✦ Honest — Pure functions (Python)",
        "crime": [
            "order.add_item(new_item)",
            "    self._items.append(item)",
            "    self._updated_at = now()",
            "order.apply_coupon(\"SAVE10\")",
            "    self._coupon_code = code",
            "    self._updated_at = now()",
            "order.calc_total()",
            "    self._total = sum(i.price for i in self._items)",
            "order.calc_discount()",
            "    CouponRegistry.get_instance()",
            "        .lookup(self._coupon_code)",
            "    self._discount = self._total * rate",
            "order.calc_tax()",
            "    TaxService.get_instance()",
            "    self._tax = svc.calculate(region, taxable)"
        ],
        "rescue": [
            "result = apply_coupon(",
            "    items, \"SAVE10\", coupons)",
            "    total = sum(price for ...)",
            "    discount = total * rate",
            "    return (total, discount, subtotal)",
            "final = calculate_tax(",
            "    result, \"NY\", tax_rates)",
            "    tax = subtotal * tax_rates[region]",
            "    return (..., tax, grand_total)"
        ]
    },
    "kotlin": {
        "crimeLabel": "☠ Dishonest — Kotlin Order class",
        "rescueLabel": "✦ Honest — Pure functions (Kotlin)",
        "crime": [
            "order.addItem(newItem)",
            "  items.add(item)",
            "  updatedAt = now()",
            "order.applyCoupon(\"SAVE10\")",
            "  couponCode = code",
            "  updatedAt = now()",
            "order.calcTotal()",
            "  total = items.sumOf { it.price }",
            "order.calcDiscount()",
            "  CouponRegistry.instance",
            "    .lookup(couponCode)",
            "  discount = total * rate",
            "order.calcTax()",
            "  TaxService.instance",
            "  tax = svc.calculate(region, taxable)"
        ],
        "rescue": [
            "val result = applyCoupon(",
            "    items, \"SAVE10\", coupons)",
            "  val total = items.sumOf { it.price }",
            "  val discount = total * rate",
            "  return Priced(total, discount, ...)",
            "val final = calculateTax(",
            "    result, \"NY\", taxRates)",
            "  val tax = subtotal * taxRates[region]",
            "  return Final(..., tax, grandTotal)"
        ]
    },
    "swift": {
        "crimeLabel": "☠ Dishonest — Swift Order class",
        "rescueLabel": "✦ Honest — Pure functions (Swift)",
        "crime": [
            "order.addItem(newItem)",
            "  items.append(item)",
            "  updatedAt = Date()",
            "order.applyCoupon(\"SAVE10\")",
            "  couponCode = code",
            "  updatedAt = Date()",
            "order.calcTotal()",
            "  total = items.reduce(0) { $0 + $1.price }",
            "order.calcDiscount()",
            "  CouponRegistry.shared",
            "    .lookup(couponCode)",
            "  discount = total * rate",
            "order.calcTax()",
            "  TaxService.shared",
            "  tax = svc.calculate(region, taxable)"
        ],
        "rescue": [
            "let result = applyCoupon(",
            "    items, \"SAVE10\", coupons)",
            "  let total = items.reduce(0) { $0 + $1.price }",
            "  let discount = total * rate",
            "  return Priced(total: total, ...)",
            "let final = calculateTax(",
            "    result, \"NY\", taxRates)",
            "  let tax = subtotal * taxRates[region]!",
            "  return Final(..., tax: tax, ...)"
        ]
    },
    "php": {
        "crimeLabel": "☠ Dishonest — PHP Order class",
        "rescueLabel": "✦ Honest — Pure functions (PHP)",
        "crime": [
            "$order->addItem($newItem);",
            "  $this->items[] = $item;",
            "  $this->updatedAt = time();",
            "$order->applyCoupon(\"SAVE10\");",
            "  $this->couponCode = $code;",
            "  $this->updatedAt = time();",
            "$order->calcTotal();",
            "  $this->total = array_sum(...);",
            "$order->calcDiscount();",
            "  CouponRegistry::getInstance()",
            "    ->lookup($this->couponCode)",
            "  $this->discount = $this->total * $rate;",
            "$order->calcTax();",
            "  TaxService::getInstance()",
            "  $this->tax = $svc->calculate($region, $taxable);"
        ],
        "rescue": [
            "$result = applyCoupon(",
            "    $items, \"SAVE10\", $coupons);",
            "  $total = array_sum(...);",
            "  $discount = $total * $rate;",
            "  return [$total, $discount, $subtotal];",
            "$final = calculateTax(",
            "    $result, \"NY\", $taxRates);",
            "  $tax = $subtotal * $taxRates[$region];",
            "  return [..., $tax, $grandTotal];"
        ]
    },
    "ruby": {
        "crimeLabel": "☠ Dishonest — Ruby Order class",
        "rescueLabel": "✦ Honest — Pure functions (Ruby)",
        "crime": [
            "order.add_item(new_item)",
            "  @items << item",
            "  @updated_at = Time.now",
            "order.apply_coupon(\"SAVE10\")",
            "  @coupon_code = code",
            "  @updated_at = Time.now",
            "order.calc_total",
            "  @total = @items.sum(&:price)",
            "order.calc_discount",
            "  CouponRegistry.instance",
            "    .lookup(@coupon_code)",
            "  @discount = @total * rate",
            "order.calc_tax",
            "  TaxService.instance",
            "  @tax = svc.calculate(region, taxable)"
        ],
        "rescue": [
            "result = apply_coupon(",
            "    items, \"SAVE10\", coupons)",
            "  total = items.sum { |i| i[1] }",
            "  discount = total * rate",
            "  [total, discount, subtotal]",
            "final = calculate_tax(",
            "    result, \"NY\", tax_rates)",
            "  tax = subtotal * tax_rates[region]",
            "  [..., tax, grand_total]"
        ]
    },
    "dart": {
        "crimeLabel": "☠ Dishonest — Dart Order class",
        "rescueLabel": "✦ Honest — Pure functions (Dart)",
        "crime": [
            "order.addItem(newItem);",
            "  items.add(item);",
            "  updatedAt = DateTime.now();",
            "order.applyCoupon(\"SAVE10\");",
            "  couponCode = code;",
            "  updatedAt = DateTime.now();",
            "order.calcTotal();",
            "  total = items.fold(0.0, ...);",
            "order.calcDiscount();",
            "  CouponRegistry.instance",
            "    .lookup(couponCode)",
            "  discount = total * rate;",
            "order.calcTax();",
            "  TaxService.instance",
            "  tax = svc.calculate(region, taxable);"
        ],
        "rescue": [
            "final result = applyCoupon(",
            "    items, \"SAVE10\", coupons);",
            "  final total = items.fold(0.0, ...);",
            "  final discount = total * rate;",
            "  return OrderResult(total, discount, ...);",
            "final fin = calculateTax(",
            "    result, \"NY\", taxRates);",
            "  final tax = subtotal * taxRates[region]!;",
            "  return FinalResult(..., tax, grandTotal);"
        ]
    },
    "cpp": {
        "crimeLabel": "☠ Dishonest — C++ Order class",
        "rescueLabel": "✦ Honest — Pure functions (C++)",
        "crime": [
            "order.addItem(newItem);",
            "  items_.push_back(item);",
            "  updatedAt_ = now();",
            "order.applyCoupon(\"SAVE10\");",
            "  couponCode_ = code;",
            "  updatedAt_ = now();",
            "order.calcTotal();",
            "  total_ = accumulate(items_...);",
            "order.calcDiscount();",
            "  CouponRegistry::getInstance()",
            "    .lookup(couponCode_)",
            "  discount_ = total_ * rate;",
            "order.calcTax();",
            "  TaxService::getInstance()",
            "  tax_ = svc.calculate(region, taxable);"
        ],
        "rescue": [
            "auto result = applyCoupon(",
            "    items, \"SAVE10\", coupons);",
            "  double total = accumulate(items...);",
            "  double discount = total * rate;",
            "  return {total, discount, subtotal};",
            "auto final = calculateTax(",
            "    result, \"NY\", taxRates);",
            "  double tax = subtotal * taxRates.at(region);",
            "  return {..., tax, grandTotal};"
        ]
    },
    "go": {
        "crimeLabel": "☠ Dishonest — Go Order struct",
        "rescueLabel": "✦ Honest — Pure functions (Go)",
        "crime": [
            "order.AddItem(newItem)",
            "  o.Items = append(o.Items, item)",
            "  o.UpdatedAt = time.Now()",
            "order.ApplyCoupon(\"SAVE10\")",
            "  o.CouponCode = code",
            "  o.UpdatedAt = time.Now()",
            "order.CalcTotal()",
            "  o.Total = sumPrices(o.Items)",
            "order.CalcDiscount()",
            "  getCouponRegistry()",
            "    .Lookup(o.CouponCode)",
            "  o.Discount = o.Total * rate",
            "order.CalcTax()",
            "  getTaxService()",
            "  o.Tax = svc.Calculate(region, taxable)"
        ],
        "rescue": [
            "result := ApplyCoupon(",
            "    items, \"SAVE10\", coupons)",
            "  total := sumPrices(items)",
            "  discount := total * rate",
            "  return OrderResult{total, discount, ...}",
            "final := CalculateTax(",
            "    result, \"NY\", taxRates)",
            "  tax := subtotal * taxRates[region]",
            "  return FinalResult{..., tax, ...}"
        ]
    }
}


def load_baseline(lang):
    with open(BASELINES_DIR / f"{lang}.json") as f:
        data = json.load(f)
    crime = {k: int(round(v)) for k, v in data["crime"].items()}
    rescue = {k: int(round(v)) for k, v in data["rescue"].items()}
    return crime, rescue


def build_crime_steps(crime_baseline, code_lines):
    assert len(code_lines) == len(CRIME_OPS), f"Expected {len(CRIME_OPS)} crime lines, got {len(code_lines)}"
    return [
        {
            "code": code_lines[i],
            "ns": crime_baseline[op],
            "op": op,
            "mutations": CRIME_MUTATIONS.get(i, []),
            "singletons": CRIME_SINGLETONS.get(i, 0),
        }
        for i, op in enumerate(CRIME_OPS)
    ]


def build_rescue_steps(rescue_baseline, code_lines):
    assert len(code_lines) == len(RESCUE_OPS), f"Expected {len(RESCUE_OPS)} rescue lines, got {len(code_lines)}"
    return [
        {
            "code": code_lines[i],
            "ns": rescue_baseline[op],
            "op": op,
            "results": RESCUE_RESULTS.get(i, []),
        }
        for i, op in enumerate(RESCUE_OPS)
    ]


def main():
    output = {}
    for lang, code in LANG_CODE.items():
        crime_baseline, rescue_baseline = load_baseline(lang)
        crime_steps = build_crime_steps(crime_baseline, code["crime"])
        rescue_steps = build_rescue_steps(rescue_baseline, code["rescue"])
        output[lang] = {
            "crimeLabel": code["crimeLabel"],
            "rescueLabel": code["rescueLabel"],
            "crime": crime_steps,
            "rescue": rescue_steps,
            "crimeTotal": sum(s["ns"] for s in crime_steps),
            "rescueTotal": sum(s["ns"] for s in rescue_steps),
        }
    # The surprise pairing is built from these steps by normalize_traces.py.
    output["surprise"] = {
        **output["cpp"],
        "crimeLabel": "\u2620 Dishonest \u2014 C++ Order class (compiled ahead of time)",
        "rescueLabel": "\u2726 Honest \u2014 Pure functions (TypeScript, V8)",
        "rescue": output["typescript"]["rescue"],
        "rescueTotal": output["typescript"]["rescueTotal"],
    }
    OUTPUT.parent.mkdir(exist_ok=True)
    OUTPUT.write_text(json.dumps(output, indent=2))
    print(f"Wrote {OUTPUT} with {len(output)} entries")


if __name__ == "__main__":
    main()
