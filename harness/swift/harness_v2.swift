// Trace harness v2 (Swift): the Honest Code order scenario.
//
// Changes from v1:
//   1. The whole scenario is timed once per iteration, not six segments on the
//      dishonest side against four on the honest side. Each segment carries one
//      clock read, so six against four charged the dishonest side two extra
//      clock reads before any code ran.
//   2. The primary number is batched: BATCH iterations between two clock reads.
//   3. The singletons are built once, before measurement. v1 reset them inside
//      the loop, which measures construction, not lookup.
//   4. Results accumulate into a global sink that is printed, the scenarios are
//      marked @inline(never), and the iteration indexes into a table of input
//      variants built before measurement. At -O the optimizer would otherwise
//      hoist the loop-invariant dictionary lookups out of the batch entirely,
//      which measures an empty loop.
//   5. Dishonest variants isolate the timestamp cost and the array growth.
//   6. Percentiles, not a lone median.
//
// The business timestamp uses Date(), the wall clock production code writes.
// The monotonic clock is used only for measurement.
//
// Build and run: swiftc -O harness_v2.swift -o harness_v2 && ./harness_v2

import Foundation
#if canImport(Dispatch)
import Dispatch
#endif

let BATCH = 1000
let REPS = 200
let WARMUP = 5000
let SAMPLES = 5000

@inline(__always)
func ns() -> UInt64 {
    return DispatchTime.now().uptimeNanoseconds
}

var sink: Double = 0

// ─────────────────────────────────────────────
// Dishonest: mutable class, separate calculation methods, singletons, stamps
// ─────────────────────────────────────────────

struct Item {
    let name: String
    let price: Double
}

final class CouponRegistry {
    static let shared = CouponRegistry()
    func lookup(_ code: String) -> Double { code == "SAVE10" ? 0.10 : 0.0 }
}

final class TaxService {
    static let shared = TaxService()
    func calculate(_ region: String, _ taxable: Double) -> Double {
        taxable * (region == "NY" ? 0.08 : 0.0725)
    }
}

// Order keeps its state in mutable fields and exposes separate, explicit
// calculation methods. Mutating methods only record input. The caller must
// run calcTotal, calcDiscount and calcTax in that order, because each reads
// the field the one before it wrote. Order matters: calcDiscount before
// calcTotal uses a stale total.
final class Order {
    private var items: [Item] = []
    private var total: Double = 0
    private var discount: Double = 0
    private var tax: Double = 0
    private var couponCode: String = ""
    private var updatedAt: Date?
    private let stamp: Bool

    init(stamp: Bool, prealloc: Bool) {
        self.stamp = stamp
        if prealloc { items.reserveCapacity(4) }
    }

    func addItem(_ item: Item) {
        items.append(item)
        if stamp { updatedAt = Date() }
    }

    func applyCoupon(_ code: String) {
        couponCode = code
        if stamp { updatedAt = Date() }
    }

    func calcTotal() {
        var t: Double = 0
        for i in items { t += i.price }
        total = t
    }

    func calcDiscount() {
        discount = total * CouponRegistry.shared.lookup(couponCode)
    }

    func calcTax() {
        tax = TaxService.shared.calculate("NY", total - discount)
    }

    func grandTotal() -> Double { total - discount + tax }
}

// The caller adds the items, applies the coupon, then runs the calculations
// in dependency order: total, discount, tax.
@inline(never)
func dishonestScenario(_ items: [Item], stamp: Bool, prealloc: Bool) -> Double {
    let order = Order(stamp: stamp, prealloc: prealloc)
    for it in items { order.addItem(it) }
    order.applyCoupon("SAVE10")
    order.calcTotal()
    order.calcDiscount()
    order.calcTax()
    return order.grandTotal()
}

// ─────────────────────────────────────────────
// Honest: pure functions, flat data
// ─────────────────────────────────────────────

struct OrderResult {
    let total: Double
    let discount: Double
    let subtotal: Double
}

struct FinalResult {
    let total: Double
    let discount: Double
    let subtotal: Double
    let tax: Double
    let grandTotal: Double
}

func applyCoupon(_ items: [Item], _ code: String,
                 _ coupons: [String: Double]) -> OrderResult {
    var total: Double = 0
    for i in items { total += i.price }
    let discount = total * (coupons[code] ?? 0)
    return OrderResult(total: total, discount: discount,
                       subtotal: total - discount)
}

func calculateTax(_ o: OrderResult, _ region: String,
                  _ taxRates: [String: Double]) -> FinalResult {
    let tax = o.subtotal * (taxRates[region] ?? 0)
    return FinalResult(total: o.total, discount: o.discount,
                       subtotal: o.subtotal, tax: tax,
                       grandTotal: o.subtotal + tax)
}

@inline(never)
func honestScenario(_ items: [Item], _ region: String,
                    _ taxRates: [String: Double],
                    _ coupons: [String: Double]) -> Double {
    let result = applyCoupon(items, "SAVE10", coupons)
    return calculateTax(result, region, taxRates).grandTotal
}

// ─────────────────────────────────────────────
// Measurement
// ─────────────────────────────────────────────

func pct(_ values: [UInt64], _ p: Double) -> UInt64 {
    let s = values.sorted()
    return s[Int(p * Double(s.count - 1))]
}

func dist(_ v: [UInt64]) -> String {
    return "{\"p05\": \(pct(v, 0.05)), \"p25\": \(pct(v, 0.25)), "
        + "\"p50\": \(pct(v, 0.50)), \"p75\": \(pct(v, 0.75)), "
        + "\"p95\": \(pct(v, 0.95))}"
}

func batched(_ f: (Int) -> Double) -> UInt64 {
    var out: [UInt64] = []
    out.reserveCapacity(REPS)
    for _ in 0..<REPS {
        let t0 = ns()
        var acc: Double = 0
        for i in 0..<BATCH { acc += f(i) }
        let dt = ns() - t0
        sink += acc
        out.append(dt / UInt64(BATCH))
    }
    return pct(out, 0.50)
}

func perIteration(_ f: (Int) -> Double) -> [UInt64] {
    var s: [UInt64] = []
    s.reserveCapacity(SAMPLES)
    for i in 0..<SAMPLES {
        let t0 = ns()
        sink += f(i)
        s.append(ns() - t0)
    }
    return s
}

func clockFloor() -> UInt64 {
    var s: [UInt64] = []
    s.reserveCapacity(SAMPLES)
    for _ in 0..<SAMPLES {
        let t0 = ns()
        let t1 = ns()
        s.append(t1 - t0)
    }
    return pct(s, 0.50)
}

// ─────────────────────────────────────────────

// Input variants are built once, before measurement.
var inputs: [[Item]] = []
inputs.reserveCapacity(256)
for v in 0..<256 {
    inputs.append([
        Item(name: "Widget", price: 29.99 + Double(v) * 0.001),
        Item(name: "Gadget", price: 39.99),
        Item(name: "Doohickey", price: 19.99),
    ])
}
let taxRates: [String: Double] = ["NY": 0.08, "CA": 0.0725]
let coupons: [String: Double] = ["SAVE10": 0.10]
let region = "NY"

// Touch the singletons once, before measurement.
_ = CouponRegistry.shared
_ = TaxService.shared

let disFull: (Int) -> Double = { i in
    dishonestScenario(inputs[i & 255], stamp: true, prealloc: false)
}
let disNoStamp: (Int) -> Double = { i in
    dishonestScenario(inputs[i & 255], stamp: false, prealloc: false)
}
let disPrealloc: (Int) -> Double = { i in
    dishonestScenario(inputs[i & 255], stamp: false, prealloc: true)
}
let hon: (Int) -> Double = { i in
    honestScenario(inputs[i & 255], region, taxRates, coupons)
}

var warm: Double = 0
for i in 0..<WARMUP {
    warm += disFull(i)
    warm += disNoStamp(i)
    warm += disPrealloc(i)
    warm += hon(i)
}
sink += warm

let floor = clockFloor()
let bFull = batched(disFull)
let bNoStamp = batched(disNoStamp)
let bPrealloc = batched(disPrealloc)
let bHonest = batched(hon)
let dFull = perIteration(disFull)
let dHonest = perIteration(hon)

print("{")
print("  \"language\": \"swift\",")
print("  \"method\": \"whole scenario, \(BATCH) iterations between two clock reads, median of \(REPS) batches\",")
print("  \"warmup_iterations\": \(WARMUP),")
print("  \"clock_read_pair_ns\": \(floor),")
print("  \"batched_ns_per_iteration\": {")
print("    \"dishonest_full\": \(bFull),")
print("    \"dishonest_no_timestamp\": \(bNoStamp),")
print("    \"dishonest_no_timestamp_prealloc\": \(bPrealloc),")
print("    \"honest\": \(bHonest)")
print("  },")
print("  \"ratios\": {")
print("    \"full_over_honest\": \(String(format: "%.2f", Double(bFull) / Double(bHonest))),")
print("    \"no_timestamp_over_honest\": \(String(format: "%.2f", Double(bNoStamp) / Double(bHonest))),")
print("    \"no_timestamp_prealloc_over_honest\": \(String(format: "%.2f", Double(bPrealloc) / Double(bHonest)))")
print("  },")
print("  \"per_iteration_ns\": {")
print("    \"note\": \"each sample includes one clock_read_pair_ns of instrument cost\",")
print("    \"dishonest_full\": \(dist(dFull)),")
print("    \"honest\":         \(dist(dHonest))")
print("  },")
print("  \"sink\": \(String(format: "%.3f", sink))")
print("}")
