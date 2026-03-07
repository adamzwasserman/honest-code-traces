import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Trace harness: captures median nanosecond costs for the six crime
 * operations and four rescue operations used in the honestcode.software
 * demo engine.
 *
 * Runs N iterations, discards warmup, reports median per operation.
 */
public class Harness {

    static final int WARMUP = 200;
    static final int RUNS = 1000;

    // ═══════════════════════════════════════════════
    // CRIME SCENE: mutable Order class with singletons
    // ═══════════════════════════════════════════════

    static class CouponRegistry {
        private static CouponRegistry instance;
        static synchronized CouponRegistry getInstance() {
            if (instance == null) instance = new CouponRegistry();
            return instance;
        }
        double lookup(String code) {
            if ("SAVE10".equals(code)) return 0.10;
            return 0;
        }
    }

    static class TaxService {
        private static TaxService instance;
        static synchronized TaxService getInstance() {
            if (instance == null) instance = new TaxService();
            return instance;
        }
        double calculate(String region, double taxable) {
            return taxable * 0.08;
        }
    }

    record Item(String name, double price) {}

    static class Order {
        final List<Item> items = new ArrayList<>();
        double total = 0;
        double discount = 0;
        double tax = 0;
        String couponCode = null;
        Instant updatedAt = null;
    }

    /** Measure individual crime operations and return [call, field, calc, single, cache, time]. */
    static long[] crimeRun() {
        var order = new Order();
        var items = List.of(
            new Item("Widget", 29.99),
            new Item("Gadget", 39.99),
            new Item("Doohickey", 19.99)
        );
        long t0, t1;

        // call: method dispatch (simulate addItem call overhead)
        t0 = System.nanoTime();
        order.items.addAll(items);
        t1 = System.nanoTime();
        long callNs = t1 - t0;

        // field: mutable field write
        t0 = System.nanoTime();
        order.couponCode = "SAVE10";
        order.discount = 0;
        t1 = System.nanoTime();
        long fieldNs = t1 - t0;

        // calc: computation (stream reduce over collection)
        t0 = System.nanoTime();
        order.total = order.items.stream().mapToDouble(Item::price).sum();
        t1 = System.nanoTime();
        long calcNs = t1 - t0;

        // single: singleton lookup
        t0 = System.nanoTime();
        var registry = CouponRegistry.getInstance();
        var taxService = TaxService.getInstance();
        t1 = System.nanoTime();
        long singleNs = (t1 - t0) / 2; // per lookup

        // cache: cache check (hash-based lookup)
        t0 = System.nanoTime();
        double rate = registry.lookup(order.couponCode);
        order.discount = order.total * rate;
        t1 = System.nanoTime();
        long cacheNs = t1 - t0;

        // time: timestamp capture
        t0 = System.nanoTime();
        order.updatedAt = Instant.now();
        t1 = System.nanoTime();
        long timeNs = t1 - t0;

        // calc tax to prevent dead code elimination
        double taxable = order.total - order.discount;
        order.tax = taxService.calculate("NY", taxable);

        return new long[]{ callNs, fieldNs, calcNs, singleNs, cacheNs, timeNs };
    }

    // ═══════════════════════════════════════════════
    // RESCUE: pure functions, flat data
    // ═══════════════════════════════════════════════

    record OrderResult(double total, double tax, double subtotal) {}
    record FinalResult(double total, double tax, double subtotal,
                       String couponCode, double discount, double grandTotal) {}

    static OrderResult calculateOrder(List<Item> items, String region,
                                      Map<String, Double> taxRates) {
        double total = 0;
        for (var item : items) total += item.price();
        double tax = total * taxRates.getOrDefault(region, 0.0);
        return new OrderResult(total, tax, total + tax);
    }

    static FinalResult applyCoupon(OrderResult order, String code,
                                   Map<String, Double> coupons) {
        double rate = coupons.getOrDefault(code, 0.0);
        double discount = order.total() * rate;
        double grandTotal = order.subtotal() - discount;
        return new FinalResult(order.total(), order.tax(), order.subtotal(),
                               code, discount, grandTotal);
    }

    /** Measure individual rescue operations and return [call, arg, calc, ret]. */
    static long[] rescueRun() {
        var items = List.of(
            new Item("Widget", 29.99),
            new Item("Gadget", 39.99),
            new Item("Doohickey", 19.99)
        );
        var taxRates = Map.of("NY", 0.08, "CA", 0.0725);
        var coupons = Map.of("SAVE10", 0.10);
        long t0, t1;

        // call: function call overhead
        t0 = System.nanoTime();
        // (measuring the call itself)
        t1 = System.nanoTime();
        long callNs = t1 - t0;

        // arg: argument passing (construct arguments)
        t0 = System.nanoTime();
        var region = "NY";
        t1 = System.nanoTime();
        long argNs = t1 - t0;

        // calc: pure computation
        t0 = System.nanoTime();
        var result = calculateOrder(items, region, taxRates);
        t1 = System.nanoTime();
        long calcNs = t1 - t0;

        // ret: return value construction
        t0 = System.nanoTime();
        var finalResult = applyCoupon(result, "SAVE10", coupons);
        t1 = System.nanoTime();
        long retNs = t1 - t0;

        // prevent dead code elimination
        if (finalResult.grandTotal() < 0) throw new RuntimeException("impossible");

        return new long[]{ callNs, argNs, calcNs, retNs };
    }

    // ═══════════════════════════════════════════════
    // HARNESS: warmup, collect, median
    // ═══════════════════════════════════════════════

    static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    public static void main(String[] args) {
        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            crimeRun();
            rescueRun();
        }

        // Collect
        long[][] crimeResults = new long[RUNS][];
        long[][] rescueResults = new long[RUNS][];
        for (int i = 0; i < RUNS; i++) {
            crimeResults[i] = crimeRun();
            rescueResults[i] = rescueRun();
        }

        // Compute medians
        String[] crimeOps = {"call", "field", "calc", "single", "cache", "time"};
        String[] rescueOps = {"call", "arg", "calc", "ret"};

        long[] crimeMedians = new long[crimeOps.length];
        for (int op = 0; op < crimeOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = crimeResults[i][op];
            crimeMedians[op] = median(col);
        }

        long[] rescueMedians = new long[rescueOps.length];
        for (int op = 0; op < rescueOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = rescueResults[i][op];
            rescueMedians[op] = median(col);
        }

        // Output JSON
        var os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        var runtime = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

        System.out.println("{");
        System.out.println("  \"language\": \"java\",");
        System.out.println("  \"os\": \"" + os + "\",");
        System.out.println("  \"runtime\": \"" + runtime + "\",");
        System.out.println("  \"runs\": " + RUNS + ",");
        System.out.println("  \"crime\": {");
        for (int i = 0; i < crimeOps.length; i++) {
            System.out.println("    \"" + crimeOps[i] + "\": " + crimeMedians[i]
                + (i < crimeOps.length - 1 ? "," : ""));
        }
        System.out.println("  },");
        System.out.println("  \"rescue\": {");
        for (int i = 0; i < rescueOps.length; i++) {
            System.out.println("    \"" + rescueOps[i] + "\": " + rescueMedians[i]
                + (i < rescueOps.length - 1 ? "," : ""));
        }
        System.out.println("  }");
        System.out.println("}");
    }
}
