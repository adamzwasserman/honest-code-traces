// Trace harness v2 (Java): the Honest Code order scenario.
//
// Changes from v1: whole scenario timed once per iteration instead of six
// segments against four; primary number batched over BATCH iterations between
// two clock reads; singletons built once before measurement rather than reset
// inside the loop; results accumulate into a volatile sink that is printed and
// the iteration indexes into a table of input variants built before
// measurement, so C2 cannot hoist the work or eliminate it; dishonest variants
// isolate the timestamp cost; percentiles instead of a lone median.
//
// Warmup is 50,000 iterations, not 200. C2 needs roughly 10,000 invocations
// before it compiles a hot method, so a 200-iteration warmup measures the
// interpreter and C1, not the compiled code.
//
// Build and run: javac HarnessV2.java && java HarnessV2

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HarnessV2 {

    static final int BATCH = 1000;
    static final int REPS = 200;
    static final int WARMUP = 50_000;
    static final int SAMPLES = 5000;

    static volatile double sink = 0;

    // ─────────────────────────────────────────
    // Dishonest: mutable class, singletons
    // ─────────────────────────────────────────

    record Item(String name, double price) {}

    static class CouponRegistry {
        private static CouponRegistry instance;
        static CouponRegistry getInstance() {
            if (instance == null) instance = new CouponRegistry();
            return instance;
        }
        double lookup(String code) { return "SAVE10".equals(code) ? 0.10 : 0.0; }
    }

    static class TaxService {
        private static TaxService instance;
        static TaxService getInstance() {
            if (instance == null) instance = new TaxService();
            return instance;
        }
        double calculate(String region, double taxable) {
            return taxable * ("NY".equals(region) ? 0.08 : 0.0725);
        }
    }

    // Order has separate, explicit calculation methods. Mutators only record
    // state. The caller must run calcTotal, calcDiscount and calcTax in that
    // order; calling one early reads stale values from the step before it.
    static class Order {
        private final List<Item> items;
        private double total, discount, tax;
        private String couponCode = "";
        private long updatedAt;
        private final boolean stamp;

        Order(boolean stamp, boolean prealloc) {
            this.stamp = stamp;
            this.items = prealloc ? new ArrayList<>(4) : new ArrayList<>();
        }

        void addItem(Item item) {
            items.add(item);
            if (stamp) updatedAt = System.currentTimeMillis();
        }

        void applyCoupon(String code) {
            couponCode = code;
            if (stamp) updatedAt = System.currentTimeMillis();
        }

        void calcTotal() {
            double t = 0;
            for (Item i : items) t += i.price();
            total = t;
        }

        void calcDiscount() {
            discount = total * CouponRegistry.getInstance().lookup(couponCode);
        }

        void calcTax() {
            tax = TaxService.getInstance().calculate("NY", total - discount);
        }

        double grandTotal() { return total - discount + tax; }
    }

    // Order matters: the calc methods run once, in dependency order, after all mutation.
    static double dishonestScenario(List<Item> items, boolean stamp, boolean prealloc) {
        Order order = new Order(stamp, prealloc);
        for (Item it : items) order.addItem(it);
        order.applyCoupon("SAVE10");
        order.calcTotal();
        order.calcDiscount();
        order.calcTax();
        return order.grandTotal();
    }

    // ─────────────────────────────────────────
    // Honest: pure functions, flat data
    // ─────────────────────────────────────────

    record OrderResult(double total, double discount, double subtotal) {}
    record FinalResult(double total, double discount, double subtotal,
                       double tax, double grandTotal) {}

    static OrderResult applyCoupon(List<Item> items, String code,
                                   Map<String, Double> coupons) {
        double total = 0;
        for (Item i : items) total += i.price();
        double discount = total * coupons.getOrDefault(code, 0.0);
        return new OrderResult(total, discount, total - discount);
    }

    static FinalResult calculateTax(OrderResult o, String region,
                                    Map<String, Double> taxRates) {
        double tax = o.subtotal() * taxRates.getOrDefault(region, 0.0);
        return new FinalResult(o.total(), o.discount(), o.subtotal(), tax,
                               o.subtotal() + tax);
    }

    static double honestScenario(List<Item> items, String region,
                                 Map<String, Double> taxRates,
                                 Map<String, Double> coupons) {
        OrderResult result = applyCoupon(items, "SAVE10", coupons);
        return calculateTax(result, region, taxRates).grandTotal();
    }

    // ─────────────────────────────────────────
    // Measurement
    // ─────────────────────────────────────────

    interface Scenario { double run(int i); }

    static long pct(long[] v, double p) {
        long[] s = v.clone();
        Arrays.sort(s);
        return s[(int) (p * (s.length - 1))];
    }

    static String dist(long[] v) {
        return String.format(
            "{\"p05\": %d, \"p25\": %d, \"p50\": %d, \"p75\": %d, \"p95\": %d}",
            pct(v, 0.05), pct(v, 0.25), pct(v, 0.50), pct(v, 0.75), pct(v, 0.95));
    }

    static long batched(Scenario f) {
        long[] out = new long[REPS];
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            double acc = 0;
            for (int i = 0; i < BATCH; i++) acc += f.run(i);
            long dt = System.nanoTime() - t0;
            sink += acc;
            out[r] = dt / BATCH;
        }
        return pct(out, 0.50);
    }

    static long[] perIteration(Scenario f) {
        long[] s = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long t0 = System.nanoTime();
            sink += f.run(i);
            s[i] = System.nanoTime() - t0;
        }
        return s;
    }

    static long clockFloor() {
        long[] s = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long t0 = System.nanoTime();
            long t1 = System.nanoTime();
            s[i] = t1 - t0;
        }
        return pct(s, 0.50);
    }

    public static void main(String[] args) {
        // Input variants are built once, before measurement, so neither side is
        // charged for constructing them.
        final int VARIANTS = 256;
        List<List<Item>> inputs = new ArrayList<>(VARIANTS);
        for (int v = 0; v < VARIANTS; v++) {
            inputs.add(List.of(new Item("Widget", 29.99 + v * 0.001),
                               new Item("Gadget", 39.99),
                               new Item("Doohickey", 19.99)));
        }
        Map<String, Double> taxRates = new HashMap<>();
        taxRates.put("NY", 0.08);
        taxRates.put("CA", 0.0725);
        Map<String, Double> coupons = new HashMap<>();
        coupons.put("SAVE10", 0.10);
        String region = "NY";

        // Build the singletons once, before measurement.
        CouponRegistry.getInstance();
        TaxService.getInstance();

        Scenario disFull = i -> dishonestScenario(inputs.get(i & 255), true, false);
        Scenario disNoStamp = i -> dishonestScenario(inputs.get(i & 255), false, false);
        Scenario disPrealloc = i -> dishonestScenario(inputs.get(i & 255), false, true);
        Scenario hon = i -> honestScenario(inputs.get(i & 255), region, taxRates, coupons);

        double warm = 0;
        for (int i = 0; i < WARMUP; i++) {
            warm += disFull.run(i);
            warm += disNoStamp.run(i);
            warm += disPrealloc.run(i);
            warm += hon.run(i);
        }
        sink += warm;

        long floor = clockFloor();
        long bFull = batched(disFull);
        long bNoStamp = batched(disNoStamp);
        long bPrealloc = batched(disPrealloc);
        long bHonest = batched(hon);
        long[] dFull = perIteration(disFull);
        long[] dHonest = perIteration(hon);

        System.out.printf("{%n");
        System.out.printf("  \"language\": \"java\",%n");
        System.out.printf("  \"runtime\": \"%s %s\",%n",
            System.getProperty("java.vm.name"), System.getProperty("java.vm.version"));
        System.out.printf("  \"method\": \"whole scenario, %d iterations between two clock reads, median of %d batches\",%n", BATCH, REPS);
        System.out.printf("  \"warmup_iterations\": %d,%n", WARMUP);
        System.out.printf("  \"clock_read_pair_ns\": %d,%n", floor);
        System.out.printf("  \"batched_ns_per_iteration\": {%n");
        System.out.printf("    \"dishonest_full\": %d,%n", bFull);
        System.out.printf("    \"dishonest_no_timestamp\": %d,%n", bNoStamp);
        System.out.printf("    \"dishonest_no_timestamp_prealloc\": %d,%n", bPrealloc);
        System.out.printf("    \"honest\": %d%n", bHonest);
        System.out.printf("  },%n");
        System.out.printf("  \"ratios\": {%n");
        System.out.printf("    \"full_over_honest\": %.2f,%n", (double) bFull / bHonest);
        System.out.printf("    \"no_timestamp_over_honest\": %.2f,%n", (double) bNoStamp / bHonest);
        System.out.printf("    \"no_timestamp_prealloc_over_honest\": %.2f%n", (double) bPrealloc / bHonest);
        System.out.printf("  },%n");
        System.out.printf("  \"per_iteration_ns\": {%n");
        System.out.printf("    \"note\": \"each sample includes one clock_read_pair_ns of instrument cost\",%n");
        System.out.printf("    \"dishonest_full\": %s,%n", dist(dFull));
        System.out.printf("    \"honest\":         %s%n", dist(dHonest));
        System.out.printf("  },%n");
        System.out.printf("  \"sink\": %.3f%n", sink);
        System.out.printf("}%n");
    }
}
