// Trace harness v2 (Kotlin): the Honest Code order scenario.
//
// Changes from v1: whole scenario timed once per iteration instead of six
// segments against four; primary number batched over BATCH iterations between
// two clock reads; singletons built once before measurement; results feed a
// volatile sink that is printed and the iteration indexes into a table of input
// variants built before measurement, so C2 cannot hoist or eliminate the work
// and neither side is charged for building its input; dishonest variants
// isolate the timestamp cost and the list growth; percentiles instead of a lone
// median.
//
// Warmup is 50,000 iterations. C2 needs roughly 10,000 invocations before it
// compiles a hot method, so a 200-iteration warmup measures the interpreter.
//
// A hand-rolled loop cannot fully stop C2 from scalar-replacing the data
// classes and hoisting the loop-invariant map lookups. For a figure that will
// be published, rebuild this on JMH with Blackhole and compare.
//
// Build and run: kotlinc harness_v2.kt -include-runtime -d harness_v2.jar
//                java -jar harness_v2.jar

import java.util.Arrays

const val BATCH = 1000
const val REPS = 200
const val WARMUP = 50_000
const val SAMPLES = 5000

@Volatile
var sink: Double = 0.0

// ─────────────────────────────────────────────
// Dishonest: mutable class, singletons, stamps
// ─────────────────────────────────────────────

data class Item(val name: String, val price: Double)

object CouponRegistry {
    fun lookup(code: String): Double = if (code == "SAVE10") 0.10 else 0.0
}

object TaxService {
    fun calculate(region: String, taxable: Double): Double =
        taxable * (if (region == "NY") 0.08 else 0.0725)
}

// Order has separate, explicit calculation methods. Mutators only record
// state. The caller must run calcTotal, calcDiscount and calcTax in that
// order; calling one early reads stale values from the step before it.
class Order(private val stamp: Boolean, prealloc: Boolean) {
    private val items: MutableList<Item> =
        if (prealloc) ArrayList(4) else ArrayList()
    private var total = 0.0
    private var discount = 0.0
    private var tax = 0.0
    private var couponCode = ""
    private var updatedAt = 0L

    fun addItem(item: Item) {
        items.add(item)
        if (stamp) updatedAt = System.currentTimeMillis()
    }

    fun applyCoupon(code: String) {
        couponCode = code
        if (stamp) updatedAt = System.currentTimeMillis()
    }

    fun calcTotal() {
        var t = 0.0
        for (i in items) t += i.price
        total = t
    }

    fun calcDiscount() {
        discount = total * CouponRegistry.lookup(couponCode)
    }

    fun calcTax() {
        tax = TaxService.calculate("NY", total - discount)
    }

    fun grandTotal(): Double = total - discount + tax
}

// Order matters: the calc methods run once, in dependency order, after all mutation.
fun dishonestScenario(items: List<Item>, stamp: Boolean, prealloc: Boolean): Double {
    val order = Order(stamp, prealloc)
    for (it in items) order.addItem(it)
    order.applyCoupon("SAVE10")
    order.calcTotal()
    order.calcDiscount()
    order.calcTax()
    return order.grandTotal()
}

// ─────────────────────────────────────────────
// Honest: pure functions, flat data
// ─────────────────────────────────────────────

data class OrderResult(val total: Double, val discount: Double, val subtotal: Double)
data class FinalResult(
    val total: Double, val discount: Double, val subtotal: Double,
    val tax: Double, val grandTotal: Double
)

fun applyCoupon(
    items: List<Item>, code: String, coupons: Map<String, Double>
): OrderResult {
    var total = 0.0
    for (i in items) total += i.price
    val discount = total * (coupons[code] ?: 0.0)
    return OrderResult(total, discount, total - discount)
}

fun calculateTax(
    o: OrderResult, region: String, taxRates: Map<String, Double>
): FinalResult {
    val tax = o.subtotal * (taxRates[region] ?: 0.0)
    return FinalResult(o.total, o.discount, o.subtotal, tax, o.subtotal + tax)
}

fun honestScenario(
    items: List<Item>, region: String,
    taxRates: Map<String, Double>, coupons: Map<String, Double>
): Double {
    val result = applyCoupon(items, "SAVE10", coupons)
    return calculateTax(result, region, taxRates).grandTotal
}

// ─────────────────────────────────────────────
// Measurement
// ─────────────────────────────────────────────

fun interface Scenario { fun run(i: Int): Double }

fun pct(v: LongArray, p: Double): Long {
    val s = v.clone()
    Arrays.sort(s)
    return s[(p * (s.size - 1)).toInt()]
}

fun dist(v: LongArray): String = String.format(
    "{\"p05\": %d, \"p25\": %d, \"p50\": %d, \"p75\": %d, \"p95\": %d}",
    pct(v, 0.05), pct(v, 0.25), pct(v, 0.50), pct(v, 0.75), pct(v, 0.95)
)

fun batched(f: Scenario): Long {
    val out = LongArray(REPS)
    for (r in 0 until REPS) {
        val t0 = System.nanoTime()
        var acc = 0.0
        for (i in 0 until BATCH) acc += f.run(i)
        val dt = System.nanoTime() - t0
        sink += acc
        out[r] = dt / BATCH
    }
    return pct(out, 0.50)
}

fun perIteration(f: Scenario): LongArray {
    val s = LongArray(SAMPLES)
    for (i in 0 until SAMPLES) {
        val t0 = System.nanoTime()
        sink += f.run(i)
        s[i] = System.nanoTime() - t0
    }
    return s
}

fun clockFloor(): Long {
    val s = LongArray(SAMPLES)
    for (i in 0 until SAMPLES) {
        val t0 = System.nanoTime()
        val t1 = System.nanoTime()
        s[i] = t1 - t0
    }
    return pct(s, 0.50)
}

fun main() {
    // Input variants are built once, before measurement.
    val variants = 256
    val inputs = ArrayList<List<Item>>(variants)
    for (v in 0 until variants) {
        inputs.add(
            listOf(
                Item("Widget", 29.99 + v * 0.001),
                Item("Gadget", 39.99),
                Item("Doohickey", 19.99)
            )
        )
    }
    val taxRates = mapOf("NY" to 0.08, "CA" to 0.0725)
    val coupons = mapOf("SAVE10" to 0.10)
    val region = "NY"

    val disFull = Scenario { i -> dishonestScenario(inputs[i and 255], true, false) }
    val disNoStamp = Scenario { i -> dishonestScenario(inputs[i and 255], false, false) }
    val disPrealloc = Scenario { i -> dishonestScenario(inputs[i and 255], false, true) }
    val hon = Scenario { i -> honestScenario(inputs[i and 255], region, taxRates, coupons) }

    var warm = 0.0
    for (i in 0 until WARMUP) {
        warm += disFull.run(i)
        warm += disNoStamp.run(i)
        warm += disPrealloc.run(i)
        warm += hon.run(i)
    }
    sink += warm

    val floor = clockFloor()
    val bFull = batched(disFull)
    val bNoStamp = batched(disNoStamp)
    val bPrealloc = batched(disPrealloc)
    val bHonest = batched(hon)
    val dFull = perIteration(disFull)
    val dHonest = perIteration(hon)

    println("{")
    println("  \"language\": \"kotlin\",")
    println("  \"runtime\": \"${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}\",")
    println("  \"method\": \"whole scenario, $BATCH iterations between two clock reads, median of $REPS batches\",")
    println("  \"warmup_iterations\": $WARMUP,")
    println("  \"clock_read_pair_ns\": $floor,")
    println("  \"batched_ns_per_iteration\": {")
    println("    \"dishonest_full\": $bFull,")
    println("    \"dishonest_no_timestamp\": $bNoStamp,")
    println("    \"dishonest_no_timestamp_prealloc\": $bPrealloc,")
    println("    \"honest\": $bHonest")
    println("  },")
    println("  \"ratios\": {")
    println("    \"full_over_honest\": ${String.format("%.2f", bFull.toDouble() / bHonest)},")
    println("    \"no_timestamp_over_honest\": ${String.format("%.2f", bNoStamp.toDouble() / bHonest)},")
    println("    \"no_timestamp_prealloc_over_honest\": ${String.format("%.2f", bPrealloc.toDouble() / bHonest)}")
    println("  },")
    println("  \"per_iteration_ns\": {")
    println("    \"note\": \"each sample includes one clock_read_pair_ns of instrument cost\",")
    println("    \"dishonest_full\": ${dist(dFull)},")
    println("    \"honest\":         ${dist(dHonest)}")
    println("  },")
    println("  \"sink\": ${String.format("%.3f", sink)}")
    println("}")
}
