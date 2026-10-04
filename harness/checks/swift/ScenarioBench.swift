// Benchmark-package checks over the scenario functions in ../swift/harness_v2.swift.
// run-checks.sh copies the declarations from that file (everything before its
// top-level measurement code) next to this one. blackHole keeps each result alive.
import Benchmark
import Foundation

let benchInputs: [[Item]] = (0..<256).map { v in
    [Item(name: "Widget", price: 29.99 + Double(v) * 0.001),
     Item(name: "Gadget", price: 39.99),
     Item(name: "Doohickey", price: 19.99)]
}
let benchTaxRates: [String: Double] = ["NY": 0.08, "CA": 0.0725]
let benchCoupons: [String: Double] = ["SAVE10": 0.10]

let benchmarks: @Sendable () -> Void = {
    let config = Benchmark.Configuration(metrics: [.wallClock], scalingFactor: .kilo, maxDuration: .seconds(5))

    Benchmark("dishonest_full", configuration: config) { benchmark in
        var i = 0
        for _ in benchmark.scaledIterations {
            blackHole(dishonestScenario(benchInputs[i & 255], stamp: true, prealloc: false))
            i += 1
        }
    }

    Benchmark("dishonest_no_timestamp", configuration: config) { benchmark in
        var i = 0
        for _ in benchmark.scaledIterations {
            blackHole(dishonestScenario(benchInputs[i & 255], stamp: false, prealloc: false))
            i += 1
        }
    }

    Benchmark("honest", configuration: config) { benchmark in
        var i = 0
        for _ in benchmark.scaledIterations {
            blackHole(honestScenario(benchInputs[i & 255], "NY", benchTaxRates, benchCoupons))
            i += 1
        }
    }
}
