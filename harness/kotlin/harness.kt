/**
 * Trace harness: captures median nanosecond costs for the six crime
 * operations and four rescue operations used in the honestcode.software
 * demo engine.
 *
 * Usage:
 *   kotlinc harness.kt -include-runtime -d harness.jar && java -jar harness.jar
 */
import kotlin.system.measureNanoTime

const val WARMUP = 200
const val RUNS = 1000

// ═══════════════════════════════════════════════
// CRIME SCENE: mutable class with singletons
// ═══════════════════════════════════════════════

class CouponRegistry private constructor() {
    companion object {
        @Volatile private var instance: CouponRegistry? = null
        fun getInstance(): CouponRegistry =
            instance ?: synchronized(this) {
                instance ?: CouponRegistry().also { instance = it }
            }
        fun reset() { instance = null }
    }
    fun lookup(code: String): Double = if (code == "SAVE10") 0.10 else 0.0
}

class TaxService private constructor() {
    companion object {
        @Volatile private var instance: TaxService? = null
        fun getInstance(): TaxService =
            instance ?: synchronized(this) {
                instance ?: TaxService().also { instance = it }
            }
        fun reset() { instance = null }
    }
    fun calculate(region: String, taxable: Double): Double = taxable * 0.08
}

data class Item(val name: String, val price: Double)

class Order {
    val items = mutableListOf<Item>()
    var total = 0.0
    var discount = 0.0
    var tax = 0.0
    var couponCode: String? = null
    var updatedAt: Long? = null
}

fun crimeRun(): LongArray {
    val order = Order()
    val items = listOf(Item("Widget", 29.99), Item("Gadget", 39.99), Item("Doohickey", 19.99))

    val callNs = measureNanoTime { order.items.addAll(items) }
    val fieldNs = measureNanoTime { order.couponCode = "SAVE10"; order.discount = 0.0 }
    val calcNs = measureNanoTime { order.total = order.items.sumOf { it.price } }

    CouponRegistry.reset(); TaxService.reset()
    var registry: CouponRegistry? = null
    var taxService: TaxService? = null
    val singleNs = measureNanoTime {
        registry = CouponRegistry.getInstance()
        taxService = TaxService.getInstance()
    } / 2

    val cacheNs = measureNanoTime {
        val rate = registry!!.lookup(order.couponCode!!)
        order.discount = order.total * rate
    }
    val timeNs = measureNanoTime { order.updatedAt = System.nanoTime() }

    order.tax = taxService!!.calculate("NY", order.total - order.discount)

    return longArrayOf(callNs, fieldNs, calcNs, singleNs, cacheNs, timeNs)
}

// ═══════════════════════════════════════════════
// RESCUE: pure functions, flat data
// ═══════════════════════════════════════════════

data class OrderResult(val total: Double, val tax: Double, val subtotal: Double)
data class FinalResult(val total: Double, val tax: Double, val subtotal: Double,
                       val couponCode: String, val discount: Double, val grandTotal: Double)

fun calculateOrder(items: List<Item>, region: String, taxRates: Map<String, Double>): OrderResult {
    val total = items.sumOf { it.price }
    val tax = total * (taxRates[region] ?: 0.0)
    return OrderResult(total, tax, total + tax)
}

fun applyCoupon(order: OrderResult, code: String, coupons: Map<String, Double>): FinalResult {
    val rate = coupons[code] ?: 0.0
    val discount = order.total * rate
    return FinalResult(order.total, order.tax, order.subtotal, code, discount, order.subtotal - discount)
}

fun rescueRun(): LongArray {
    val items = listOf(Item("Widget", 29.99), Item("Gadget", 39.99), Item("Doohickey", 19.99))
    val taxRates = mapOf("NY" to 0.08, "CA" to 0.0725)
    val coupons = mapOf("SAVE10" to 0.10)

    val callNs = measureNanoTime { }
    val argNs = measureNanoTime { @Suppress("UNUSED_VARIABLE") val r = "NY" }
    var result: OrderResult? = null
    val calcNs = measureNanoTime { result = calculateOrder(items, "NY", taxRates) }
    var final_: FinalResult? = null
    val retNs = measureNanoTime { final_ = applyCoupon(result!!, "SAVE10", coupons) }
    check(final_!!.grandTotal > 0)

    return longArrayOf(callNs, argNs, calcNs, retNs)
}

// ═══════════════════════════════════════════════
// HARNESS
// ═══════════════════════════════════════════════

fun median(values: LongArray): Long {
    values.sort()
    return values[values.size / 2]
}

fun main() {
    repeat(WARMUP) { crimeRun(); rescueRun() }

    val crimeResults = Array(RUNS) { crimeRun() }
    val rescueResults = Array(RUNS) { rescueRun() }

    val crimeOps = listOf("call", "field", "calc", "single", "cache", "time")
    val rescueOps = listOf("call", "arg", "calc", "ret")

    val crime = crimeOps.mapIndexed { i, op ->
        op to median(LongArray(RUNS) { crimeResults[it][i] })
    }.toMap()

    val rescue = rescueOps.mapIndexed { i, op ->
        op to median(LongArray(RUNS) { rescueResults[it][i] })
    }.toMap()

    val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
    val runtime = "${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}"

    println(buildString {
        appendLine("{")
        appendLine("""  "language": "kotlin",""")
        appendLine("""  "os": "$os",""")
        appendLine("""  "runtime": "$runtime",""")
        appendLine("""  "runs": $RUNS,""")
        appendLine("""  "crime": {""")
        crime.entries.forEachIndexed { i, (k, v) ->
            appendLine("""    "$k": $v${if (i < crime.size - 1) "," else ""}""")
        }
        appendLine("  },")
        appendLine("""  "rescue": {""")
        rescue.entries.forEachIndexed { i, (k, v) ->
            appendLine("""    "$k": $v${if (i < rescue.size - 1) "," else ""}""")
        }
        appendLine("  }")
        appendLine("}")
    }.trimEnd())
}
