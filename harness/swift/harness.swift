// Trace harness: captures median nanosecond costs for the six crime
// operations and four rescue operations used in the honestcode.software
// demo engine.
//
// Uses batched operations (100 iterations per measurement) to overcome
// timer granularity with optimized code.
//
// Usage:
//   swiftc -O harness.swift -o harness && ./harness

import Foundation
#if canImport(Glibc)
import Glibc
#endif

let WARMUP = 200
let RUNS = 1000
let BATCH = 100

// ═══════════════════════════════════════════════
// CRIME SCENE: mutable class with singletons
// ═══════════════════════════════════════════════

class CouponRegistry {
    static var instance: CouponRegistry?
    static func getInstance() -> CouponRegistry {
        if instance == nil { instance = CouponRegistry() }
        return instance!
    }
    static func reset() { instance = nil }
    func lookup(_ code: String) -> Double {
        return code == "SAVE10" ? 0.10 : 0
    }
}

class TaxService {
    static var instance: TaxService?
    static func getInstance() -> TaxService {
        if instance == nil { instance = TaxService() }
        return instance!
    }
    static func reset() { instance = nil }
    func calculate(_ region: String, _ taxable: Double) -> Double {
        return taxable * 0.08
    }
}

struct Item {
    let name: String
    let price: Double
}

class Order {
    var items: [Item] = []
    var total: Double = 0
    var discount: Double = 0
    var tax: Double = 0
    var couponCode: String? = nil
    var updatedAt: UInt64? = nil
}

func ns() -> UInt64 {
    var time = timespec()
    clock_gettime(CLOCK_MONOTONIC, &time)
    return UInt64(time.tv_sec) * 1_000_000_000 + UInt64(time.tv_nsec)
}

func crimeRun() -> [Int64] {
    let items = [Item(name: "Widget", price: 29.99), Item(name: "Gadget", price: 39.99), Item(name: "Doohickey", price: 19.99)]

    // call: append items
    var t0 = ns()
    for _ in 0..<BATCH {
        let order = Order()
        order.items.append(contentsOf: items)
    }
    let callNs = Int64(ns() - t0) / Int64(BATCH)

    // field: mutable field write
    t0 = ns()
    for _ in 0..<BATCH {
        let order = Order()
        order.couponCode = "SAVE10"
        order.discount = 0
    }
    let fieldNs = Int64(ns() - t0) / Int64(BATCH)

    // calc: computation
    let order = Order()
    order.items = items
    t0 = ns()
    for _ in 0..<BATCH {
        order.total = order.items.reduce(0) { $0 + $1.price }
    }
    let calcNs = Int64(ns() - t0) / Int64(BATCH)

    // single: singleton lookup
    t0 = ns()
    for _ in 0..<BATCH {
        CouponRegistry.reset()
        TaxService.reset()
        _ = CouponRegistry.getInstance()
        _ = TaxService.getInstance()
    }
    let singleNs = Int64(ns() - t0) / Int64(BATCH) / 2

    // cache: lookup
    let registry = CouponRegistry.getInstance()
    t0 = ns()
    for _ in 0..<BATCH {
        let rate = registry.lookup("SAVE10")
        order.discount = order.total * rate
    }
    let cacheNs = Int64(ns() - t0) / Int64(BATCH)

    // time: timestamp
    t0 = ns()
    for _ in 0..<BATCH {
        order.updatedAt = ns()
    }
    let timeNs = Int64(ns() - t0) / Int64(BATCH)

    let taxService = TaxService.getInstance()
    order.tax = taxService.calculate("NY", order.total - order.discount)

    return [callNs, fieldNs, calcNs, singleNs, cacheNs, timeNs]
}

// ═══════════════════════════════════════════════
// RESCUE: pure functions, flat data
// ═══════════════════════════════════════════════

struct OrderResult { let total: Double; let tax: Double; let subtotal: Double }
struct FinalResult { let total: Double; let tax: Double; let subtotal: Double; let couponCode: String; let discount: Double; let grandTotal: Double }

func calculateOrder(_ items: [Item], _ region: String, _ taxRates: [String: Double]) -> OrderResult {
    let total = items.reduce(0) { $0 + $1.price }
    let tax = total * (taxRates[region] ?? 0)
    return OrderResult(total: total, tax: tax, subtotal: total + tax)
}

func applyCoupon(_ order: OrderResult, _ code: String, _ coupons: [String: Double]) -> FinalResult {
    let rate = coupons[code] ?? 0
    let discount = order.total * rate
    return FinalResult(total: order.total, tax: order.tax, subtotal: order.subtotal, couponCode: code, discount: discount, grandTotal: order.subtotal - discount)
}

func rescueRun() -> [Int64] {
    let items = [Item(name: "Widget", price: 29.99), Item(name: "Gadget", price: 39.99), Item(name: "Doohickey", price: 19.99)]
    let taxRates = ["NY": 0.08, "CA": 0.0725]
    let coupons = ["SAVE10": 0.10]

    // call: overhead
    var t0 = ns()
    for _ in 0..<BATCH { _ = ns() }
    let callNs = Int64(ns() - t0) / Int64(BATCH)

    // arg: passing
    t0 = ns()
    for _ in 0..<BATCH { _ = "NY" }
    let argNs = Int64(ns() - t0) / Int64(BATCH)

    // calc: pure computation
    var result = OrderResult(total: 0, tax: 0, subtotal: 0)
    t0 = ns()
    for _ in 0..<BATCH {
        result = calculateOrder(items, "NY", taxRates)
    }
    let calcNs = Int64(ns() - t0) / Int64(BATCH)

    // ret: return value
    var final_ = FinalResult(total: 0, tax: 0, subtotal: 0, couponCode: "", discount: 0, grandTotal: 0)
    t0 = ns()
    for _ in 0..<BATCH {
        final_ = applyCoupon(result, "SAVE10", coupons)
    }
    let retNs = Int64(ns() - t0) / Int64(BATCH)

    precondition(final_.grandTotal > 0)
    return [callNs, argNs, calcNs, retNs]
}

// ═══════════════════════════════════════════════
// HARNESS
// ═══════════════════════════════════════════════

func median(_ values: [Int64]) -> Int64 {
    return values.sorted()[values.count / 2]
}

for _ in 0..<WARMUP { _ = crimeRun(); _ = rescueRun() }

var crimeResults = [[Int64]]()
var rescueResults = [[Int64]]()
for _ in 0..<RUNS {
    crimeResults.append(crimeRun())
    rescueResults.append(rescueRun())
}

let crimeOps = ["call", "field", "calc", "single", "cache", "time"]
let rescueOps = ["call", "arg", "calc", "ret"]

var crime = [String: Int64]()
for (i, op) in crimeOps.enumerated() {
    crime[op] = median(crimeResults.map { $0[i] })
}

var rescue = [String: Int64]()
for (i, op) in rescueOps.enumerated() {
    rescue[op] = median(rescueResults.map { $0[i] })
}

#if os(Linux)
let osName = "Linux"
#else
let osName = "macOS"
#endif

print("{")
print("  \"language\": \"swift\",")
print("  \"os\": \"\(osName)\",")
print("  \"runtime\": \"Swift\",")
print("  \"runs\": \(RUNS),")
print("  \"crime\": {")
for (i, op) in crimeOps.enumerated() {
    let comma = i < crimeOps.count - 1 ? "," : ""
    print("    \"\(op)\": \(crime[op]!)\(comma)")
}
print("  },")
print("  \"rescue\": {")
for (i, op) in rescueOps.enumerated() {
    let comma = i < rescueOps.count - 1 ? "," : ""
    print("    \"\(op)\": \(rescue[op]!)\(comma)")
}
print("  }")
print("}")
