"""
Generate canonical trace files for honestcode.software from harness baselines.

Each step in the output maps 1:1 to an animation frame on the website.
The harness measures 6 crime categories (call, field, calc, single, cache, time)
and 4 rescue categories (call, arg, calc, ret). This script maps each category
to the specific steps it covers, producing the full step-by-step trace with
code text and nanosecond timing.

Usage:
    uv run python generate_traces.py
    # reads harness/baselines/*.json
    # writes traces/landing-page.json
"""
import json
import math
from pathlib import Path

BASELINES_DIR = Path(__file__).parent / "harness" / "baselines"
OUTPUT = Path(__file__).parent / "traces" / "landing-page.json"

# 27 crime steps: which harness category provides the timing for each step
# These match the actual operations in the harness code
CRIME_OPS = [
    "call",    # addItem call
    "field",   # items mutation
    "call",    # recalculateTotal call
    "calc",    # total calculation
    "call",    # recalculateDiscount call
    "single",  # singleton lookup
    "cache",   # cache check
    "cache",   # cache read
    "field",   # discount field write
    "call",    # recalculateTax call
    "single",  # singleton lookup
    "cache",   # cache check
    "cache",   # cache read
    "field",   # tax field write
    "time",    # timestamp
    "call",    # applyCoupon call
    "field",   # couponCode mutation
    "call",    # recalculateDiscount call
    "single",  # singleton lookup
    "cache",   # cache check
    "cache",   # stale cache read
    "field",   # discount field write
    "call",    # recalculateTax call
    "single",  # singleton lookup
    "cache",   # cache check
    "field",   # tax field write
    "time",    # timestamp
]

# State mutations triggered by each crime step (index -> [[id, value]])
CRIME_MUTATIONS = {
    3:  [["s-total", "89.97"]],
    8:  [["s-discount", "0.00"]],
    13: [["s-tax", "7.20"]],
    14: [["s-updated", "14:23:07.4"]],
    16: [["s-coupon", '"SAVE10"']],
    21: [["s-discount", "9.00"]],
    25: [["s-tax", "6.48"]],
    26: [["s-updated", "14:23:07.5"]],
}

# Singleton count per crime step
CRIME_SINGLETONS = {5: 1, 10: 1, 18: 1, 23: 1}

# 9 rescue steps — ops must match code lines
RESCUE_OPS = [
    "call",  # calculateOrder(
    "arg",   # items, region, taxRates)
    "calc",  # total = sum/reduce(...)
    "calc",  # tax = total * rate
    "ret",   # return {total, tax, subtotal}
    "call",  # applyCoupon(
    "arg",   # result, "SAVE10", coupons)
    "calc",  # discount = total * rate
    "ret",   # return {discount, grand_total}
]

# Results displayed by each rescue step
RESCUE_RESULTS = {
    2: [["r-total", "89.97"]],
    3: [["r-tax", "7.20"]],
    4: [["r-subtotal", "97.17"]],
    5: [["r-coupon", '"SAVE10"']],
    7: [["r-discount", "9.00"]],
    8: [["r-grand", "87.45"]],
}

# Per-language code lines (27 crime, 9 rescue)
LANG_CODE = {
    "java": {
        "crimeLabel": "☠ Dishonest — Java Order class",
        "rescueLabel": "✦ Honest — Pure functions (Java)",
        "crime": [
            "order.addItem(newItem);",
            "  this.items.add(item);",
            "  recalculateTotal();",
            "    this.total = items.stream()...",
            "  recalculateDiscount();",
            "    CouponRegistry.getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    this.discount = coupon.apply()",
            "  recalculateTax();",
            "    TaxService.getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    this.tax = taxable * rate",
            "  this.updatedAt = now();",
            "order.applyCoupon(\"SAVE10\");",
            "  this.couponCode = code;",
            "  recalculateDiscount();",
            "    CouponRegistry.getInstance()",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    this.discount = coupon.apply()",
            "  recalculateTax();",
            "    TaxService.getInstance()",
            "      -> global lookup...",
            "    this.tax = taxable * rate",
            "  this.updatedAt = now();",
        ],
        "rescue": [
            "var result = calculateOrder(",
            "    items, \"NY\", taxRates);",
            "  var total = items.stream()...",
            "  var tax = total * taxRates.get(region)",
            '  return Map.of("total", "tax", ...);',
            "var final = applyCoupon(",
            '    result, "SAVE10", coupons);',
            "  var discount = result.total * rate;",
            "  return new Result(total, discount, ...);",
        ],
    },
    "typescript": {
        "crimeLabel": "☠ Dishonest — TypeScript Order class",
        "rescueLabel": "✦ Honest — Pure functions (TypeScript)",
        "crime": [
            "order.addItem(newItem);",
            "  this.items.push(item);",
            "  this.recalculateTotal();",
            "    this.total = this.items.reduce(..)",
            "  this.recalculateDiscount();",
            "    CouponRegistry.getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    this.discount = coupon.apply()",
            "  this.recalculateTax();",
            "    TaxService.getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    this.tax = taxable * rate;",
            "  this.updatedAt = new Date();",
            'order.applyCoupon("SAVE10");',
            "  this.couponCode = code;",
            "  this.recalculateDiscount();",
            "    CouponRegistry.getInstance()",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    this.discount = coupon.apply()",
            "  this.recalculateTax();",
            "    TaxService.getInstance()",
            "      -> global lookup...",
            "    this.tax = taxable * rate;",
            "  this.updatedAt = new Date();",
        ],
        "rescue": [
            "const result = calculateOrder(",
            '    items, "NY", taxRates);',
            "  const total = items.reduce(...)",
            "  const tax = total * taxRates[region]",
            "  return { total, tax, subtotal };",
            "const final = applyCoupon(",
            '    result, "SAVE10", coupons);',
            "  const discount = result.total * rate;",
            "  return { ...result, discount, grand };",
        ],
    },
    "csharp": {
        "crimeLabel": "☠ Dishonest — C# Order class",
        "rescueLabel": "✦ Honest — Pure functions (C#)",
        "crime": [
            "order.AddItem(newItem);",
            "  _items.Add(item);",
            "  RecalculateTotal();",
            "    _total = _items.Sum(i => i.Price);",
            "  RecalculateDiscount();",
            "    CouponRegistry.Instance",
            "      -> global lookup...",
            "      -> cache check...",
            "    _discount = coupon.Apply()",
            "  RecalculateTax();",
            "    TaxService.Instance",
            "      -> global lookup...",
            "      -> cache check...",
            "    _tax = taxable * rate;",
            "  _updatedAt = DateTime.UtcNow;",
            'order.ApplyCoupon("SAVE10");',
            "  _couponCode = code;",
            "  RecalculateDiscount();",
            "    CouponRegistry.Instance",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    _discount = coupon.Apply()",
            "  RecalculateTax();",
            "    TaxService.Instance",
            "      -> global lookup...",
            "    _tax = taxable * rate;",
            "  _updatedAt = DateTime.UtcNow;",
        ],
        "rescue": [
            "var result = CalculateOrder(",
            '    items, "NY", taxRates);',
            "  var total = items.Sum(i => i.Price);",
            "  var tax = total * taxRates[region];",
            "  return new OrderResult(total, tax, ...);",
            "var final = ApplyCoupon(",
            '    result, "SAVE10", coupons);',
            "  var discount = result.Total * rate;",
            "  return new FinalResult(total, ...);",
        ],
    },
    "python": {
        "crimeLabel": "☠ Dishonest — Python Order class",
        "rescueLabel": "✦ Honest — Pure functions (Python)",
        "crime": [
            "order.add_item(new_item)",
            "  self.items.append(item)",
            "  self._recalculate_total()",
            "    self.total = sum(i.price for ...)",
            "  self._recalculate_discount()",
            "    CouponRegistry.get_instance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    self.discount = coupon.apply()",
            "  self._recalculate_tax()",
            "    TaxService.get_instance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    self.tax = taxable * rate",
            "  self.updated_at = datetime.now()",
            'order.apply_coupon("SAVE10")',
            "  self.coupon_code = code",
            "  self._recalculate_discount()",
            "    CouponRegistry.get_instance()",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    self.discount = coupon.apply()",
            "  self._recalculate_tax()",
            "    TaxService.get_instance()",
            "      -> global lookup...",
            "    self.tax = taxable * rate",
            "  self.updated_at = datetime.now()",
        ],
        "rescue": [
            "result = calculate_order(",
            '    items, "NY", tax_rates)',
            '  total = sum(i["price"] for ...)',
            "  tax = total * tax_rates[region]",
            '  return total, tax, subtotal',
            "final = apply_coupon(",
            '    result, "SAVE10", coupons)',
            '  discount = total * rate',
            '  return code, discount, grand_total',
        ],
    },
    "kotlin": {
        "crimeLabel": "☠ Dishonest — Kotlin Order class",
        "rescueLabel": "✦ Honest — Pure functions (Kotlin)",
        "crime": [
            "order.addItem(newItem)",
            "  items.add(item)",
            "  recalculateTotal()",
            "    total = items.sumOf { it.price }",
            "  recalculateDiscount()",
            "    CouponRegistry.getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    discount = coupon.apply()",
            "  recalculateTax()",
            "    TaxService.getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    tax = taxable * rate",
            "  updatedAt = Instant.now()",
            'order.applyCoupon("SAVE10")',
            "  couponCode = code",
            "  recalculateDiscount()",
            "    CouponRegistry.getInstance()",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    discount = coupon.apply()",
            "  recalculateTax()",
            "    TaxService.getInstance()",
            "      -> global lookup...",
            "    tax = taxable * rate",
            "  updatedAt = Instant.now()",
        ],
        "rescue": [
            "val result = calculateOrder(",
            '    items, "NY", taxRates)',
            "  val total = items.sumOf { it.price }",
            "  val tax = total * taxRates[region]",
            '  return Triple(total, tax, subtotal)',
            "val final = applyCoupon(",
            '    result, "SAVE10", coupons)',
            "  val discount = result.total * rate",
            "  return Triple(code, discount, grand)",
        ],
    },
    "swift": {
        "crimeLabel": "☠ Dishonest — Swift Order class",
        "rescueLabel": "✦ Honest — Pure functions (Swift)",
        "crime": [
            "order.addItem(newItem)",
            "  items.append(item)",
            "  recalculateTotal()",
            "    total = items.reduce(0) { ... }",
            "  recalculateDiscount()",
            "    CouponRegistry.shared",
            "      -> global lookup...",
            "      -> cache check...",
            "    discount = coupon.apply()",
            "  recalculateTax()",
            "    TaxService.shared",
            "      -> global lookup...",
            "      -> cache check...",
            "    tax = taxable * rate",
            "  updatedAt = Date()",
            'order.applyCoupon("SAVE10")',
            "  couponCode = code",
            "  recalculateDiscount()",
            "    CouponRegistry.shared",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    discount = coupon.apply()",
            "  recalculateTax()",
            "    TaxService.shared",
            "      -> global lookup...",
            "    tax = taxable * rate",
            "  updatedAt = Date()",
        ],
        "rescue": [
            "let result = calculateOrder(",
            '    items, region: "NY", rates: taxRates)',
            "  let total = items.reduce(0) { ... }",
            "  let tax = total * taxRates[region]",
            "  return OrderResult(total, tax, ...)",
            "let final = applyCoupon(",
            '    result, "SAVE10", coupons)',
            "  let discount = result.total * rate",
            "  return FinalResult(total, discount, ...)",
        ],
    },
    "php": {
        "crimeLabel": "☠ Dishonest — PHP Order class",
        "rescueLabel": "✦ Honest — Pure functions (PHP)",
        "crime": [
            "$order->addItem($newItem);",
            "  $this->items[] = $item;",
            "  $this->recalculateTotal();",
            "    $this->total = array_sum(...);",
            "  $this->recalculateDiscount();",
            "    CouponRegistry::getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    $this->discount = $coupon->apply()",
            "  $this->recalculateTax();",
            "    TaxService::getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    $this->tax = $taxable * $rate;",
            "  $this->updatedAt = new DateTime();",
            '$order->applyCoupon("SAVE10");',
            "  $this->couponCode = $code;",
            "  $this->recalculateDiscount();",
            "    CouponRegistry::getInstance()",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    $this->discount = $coupon->apply()",
            "  $this->recalculateTax();",
            "    TaxService::getInstance()",
            "      -> global lookup...",
            "    $this->tax = $taxable * $rate;",
            "  $this->updatedAt = new DateTime();",
        ],
        "rescue": [
            "$result = calculate_order(",
            '    $items, "NY", $taxRates);',
            "  $total = array_sum(...);",
            "  $tax = $total * $taxRates[$region];",
            '  return ["total" => $total, ...];',
            "$final = apply_coupon(",
            '    $result, "SAVE10", $coupons);',
            '  $discount = $result["total"] * $rate;',
            "  return array_merge($result, ...);",
        ],
    },
    "ruby": {
        "crimeLabel": "☠ Dishonest — Ruby Order class",
        "rescueLabel": "✦ Honest — Pure functions (Ruby)",
        "crime": [
            "order.add_item(new_item)",
            "  @items << item",
            "  recalculate_total",
            "    @total = @items.sum(&:price)",
            "  recalculate_discount",
            "    CouponRegistry.instance",
            "      -> global lookup...",
            "      -> cache check...",
            "    @discount = coupon.apply",
            "  recalculate_tax",
            "    TaxService.instance",
            "      -> global lookup...",
            "      -> cache check...",
            "    @tax = taxable * rate",
            "  @updated_at = Time.now",
            'order.apply_coupon("SAVE10")',
            "  @coupon_code = code",
            "  recalculate_discount",
            "    CouponRegistry.instance",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    @discount = coupon.apply",
            "  recalculate_tax",
            "    TaxService.instance",
            "      -> global lookup...",
            "    @tax = taxable * rate",
            "  @updated_at = Time.now",
        ],
        "rescue": [
            "result = calculate_order(",
            '    items, "NY", tax_rates)',
            "  total = items.sum { |i| i[:price] }",
            "  tax = total * tax_rates[region]",
            "  { total: total, tax: tax, ... }",
            "final = apply_coupon(",
            '    result, "SAVE10", coupons)',
            "  discount = result[:total] * rate",
            "  result.merge(discount: discount, ...)",
        ],
    },
    "dart": {
        "crimeLabel": "☠ Dishonest — Dart Order class",
        "rescueLabel": "✦ Honest — Pure functions (Dart)",
        "crime": [
            "order.addItem(newItem);",
            "  _items.add(item);",
            "  _recalculateTotal();",
            "    _total = _items.fold(0, ...);",
            "  _recalculateDiscount();",
            "    CouponRegistry.instance",
            "      -> global lookup...",
            "      -> cache check...",
            "    _discount = coupon.apply();",
            "  _recalculateTax();",
            "    TaxService.instance",
            "      -> global lookup...",
            "      -> cache check...",
            "    _tax = taxable * rate;",
            "  _updatedAt = DateTime.now();",
            'order.applyCoupon("SAVE10");',
            "  _couponCode = code;",
            "  _recalculateDiscount();",
            "    CouponRegistry.instance",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    _discount = coupon.apply();",
            "  _recalculateTax();",
            "    TaxService.instance",
            "      -> global lookup...",
            "    _tax = taxable * rate;",
            "  _updatedAt = DateTime.now();",
        ],
        "rescue": [
            "final result = calculateOrder(",
            '    items, "NY", taxRates);',
            "  final total = items.fold(0, ...);",
            "  final tax = total * taxRates[region];",
            '  return {"total": total, ...};',
            "final withCoupon = applyCoupon(",
            '    result, "SAVE10", coupons);',
            '  final discount = result["total"] * rate;',
            '  return {...result, "discount": ...};',
        ],
    },
    "cpp": {
        "crimeLabel": "☠ Dishonest — C++ Order class",
        "rescueLabel": "✦ Honest — Pure functions (C++)",
        "crime": [
            "order.addItem(newItem);",
            "  items_.push_back(item);",
            "  recalculateTotal();",
            "    total_ = std::accumulate(...);",
            "  recalculateDiscount();",
            "    CouponRegistry::getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    discount_ = coupon->apply();",
            "  recalculateTax();",
            "    TaxService::getInstance()",
            "      -> global lookup...",
            "      -> cache check...",
            "    tax_ = taxable * rate;",
            "  updatedAt_ = system_clock::now();",
            'order.applyCoupon("SAVE10");',
            "  couponCode_ = code;",
            "  recalculateDiscount();",
            "    CouponRegistry::getInstance()",
            "      -> global lookup...",
            "      -> cache check (stale?)...",
            "    discount_ = coupon->apply();",
            "  recalculateTax();",
            "    TaxService::getInstance()",
            "      -> global lookup...",
            "    tax_ = taxable * rate;",
            "  updatedAt_ = system_clock::now();",
        ],
        "rescue": [
            "auto result = calculate_order(",
            '    items, "NY", tax_rates);',
            "  auto total = std::accumulate(...);",
            "  auto tax = total * tax_rates.at(region);",
            "  return OrderResult{total, tax, ...};",
            "auto final = apply_coupon(",
            '    result, "SAVE10", coupons);',
            "  auto discount = result.total * rate;",
            "  return FinalResult{total, discount, ...};",
        ],
    },
    "go": {
        "crimeLabel": "☠ Dishonest — Go Order struct",
        "rescueLabel": "✦ Honest — Pure functions (Go)",
        "crime": [
            "order.AddItem(newItem)",
            "  o.Items = append(o.Items, item)",
            "  o.recalculateTotal()",
            "    o.Total = sumItems(o.Items)",
            "  o.recalculateDiscount()",
            "    couponRegistry.GetInstance()",
            "      -> global lookup...",
            "      -> sync.Once check...",
            "    o.Discount = coupon.Apply()",
            "  o.recalculateTax()",
            "    taxService.GetInstance()",
            "      -> global lookup...",
            "      -> sync.Once check...",
            "    o.Tax = taxable * rate",
            "  o.UpdatedAt = time.Now()",
            'order.ApplyCoupon("SAVE10")',
            "  o.CouponCode = code",
            "  o.recalculateDiscount()",
            "    couponRegistry.GetInstance()",
            "      -> global lookup...",
            "      -> sync.Once check (stale?)...",
            "    o.Discount = coupon.Apply()",
            "  o.recalculateTax()",
            "    taxService.GetInstance()",
            "      -> global lookup...",
            "    o.Tax = taxable * rate",
            "  o.UpdatedAt = time.Now()",
        ],
        "rescue": [
            "result := CalculateOrder(",
            '    items, "NY", taxRates)',
            "  total := SumItems(items)",
            "  tax := total * taxRates[region]",
            "  return OrderResult{Total, Tax, ...}",
            "final := ApplyCoupon(",
            '    result, "SAVE10", coupons)',
            "  discount := result.Total * rate",
            "  return FinalResult{Total, Discount, ...}",
        ],
    },
}

# Surprise pairing: Java crime + Python rescue
SURPRISE = {
    "crimeLabel": "☠ Dishonest — Java Order class (JVM)",
    "rescueLabel": "✦ Honest — Pure functions (Python)",
    "crime_lang": "java",
    "rescue_lang": "python",
}


def load_baseline(lang):
    path = BASELINES_DIR / f"{lang}.json"
    with open(path) as f:
        data = json.load(f)
    # Normalize values to int
    crime = {k: int(round(v)) for k, v in data["crime"].items()}
    rescue = {k: int(round(v)) for k, v in data["rescue"].items()}
    return crime, rescue


def build_crime_steps(crime_baseline, code_lines):
    assert len(code_lines) == 27, f"Expected 27 crime code lines, got {len(code_lines)}"
    steps = []
    for i, op in enumerate(CRIME_OPS):
        steps.append({
            "code": code_lines[i],
            "ns": crime_baseline[op],
            "op": op,
            "mutations": CRIME_MUTATIONS.get(i, []),
            "singletons": CRIME_SINGLETONS.get(i, 0),
        })
    return steps


def build_rescue_steps(rescue_baseline, code_lines):
    assert len(code_lines) == 9, f"Expected 9 rescue code lines, got {len(code_lines)}"
    steps = []
    for i, op in enumerate(RESCUE_OPS):
        steps.append({
            "code": code_lines[i],
            "ns": rescue_baseline[op],
            "op": op,
            "results": RESCUE_RESULTS.get(i, []),
        })
    return steps


def main():
    output = {}

    for lang, code in LANG_CODE.items():
        crime_baseline, rescue_baseline = load_baseline(lang)
        crime_steps = build_crime_steps(crime_baseline, code["crime"])
        rescue_steps = build_rescue_steps(rescue_baseline, code["rescue"])

        crime_total = sum(s["ns"] for s in crime_steps)
        rescue_total = sum(s["ns"] for s in rescue_steps)

        output[lang] = {
            "crimeLabel": code["crimeLabel"],
            "rescueLabel": code["rescueLabel"],
            "crime": crime_steps,
            "rescue": rescue_steps,
            "crimeTotal": crime_total,
            "rescueTotal": rescue_total,
        }

    # Surprise: Java crime + Python rescue
    java_crime, _ = load_baseline("java")
    _, python_rescue = load_baseline("python")
    surprise_crime = build_crime_steps(java_crime, LANG_CODE["java"]["crime"])
    surprise_rescue = build_rescue_steps(python_rescue, LANG_CODE["python"]["rescue"])

    output["surprise"] = {
        "crimeLabel": SURPRISE["crimeLabel"],
        "rescueLabel": SURPRISE["rescueLabel"],
        "crime": surprise_crime,
        "rescue": surprise_rescue,
        "crimeTotal": sum(s["ns"] for s in surprise_crime),
        "rescueTotal": sum(s["ns"] for s in surprise_rescue),
    }

    OUTPUT.parent.mkdir(exist_ok=True)
    with open(OUTPUT, "w") as f:
        json.dump(output, f, indent=2)

    # Print summary
    print(f"Generated {OUTPUT}")
    print(f"Languages: {len(output)}")
    print()
    print(f"{'Language':<13} {'Crime':>7} {'Rescue':>7} {'Ratio':>6}")
    print("-" * 35)
    for lang, data in output.items():
        ct, rt = data["crimeTotal"], data["rescueTotal"]
        ratio = f"{ct/rt:.1f}x" if rt > 0 else "n/a"
        print(f"{lang:<13} {ct:>6}ns {rt:>6}ns {ratio:>6}")


if __name__ == "__main__":
    main()
