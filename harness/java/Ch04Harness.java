import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Ch.4 harness: measures nanosecond cost of mutable object with
 * singleton lookups (where execution order changes results) vs
 * pure functions with data passed as arguments.
 *
 * The crime: Order class mutates internal state AND reaches out to
 * global singletons (CouponRegistry.getInstance(), TaxService.getInstance()).
 * Method call order changes the final total.
 *
 * The rescue: pure functions take all data as parameters, return new values.
 * Any call order produces the same result.
 *
 * Usage: javac Ch04Harness.java && java Ch04Harness
 */
public class Ch04Harness {

    static final int WARMUP = 5000;
    static final int RUNS   = 1000;

    static volatile long sink;

    // ═══════════════════════════════════════════════
    // CRIME: mutable Order with singleton lookups
    // ═══════════════════════════════════════════════

    // Singletons (the hidden dependencies)
    static class CouponRegistry {
        private static CouponRegistry instance;
        private final HashMap<String, Double> coupons = new HashMap<>();
        private CouponRegistry() {
            coupons.put("SAVE10", 0.10);
            coupons.put("SAVE20", 0.20);
        }
        static synchronized CouponRegistry getInstance() {
            if (instance == null) instance = new CouponRegistry();
            return instance;
        }
        double lookup(String code) {
            return coupons.getOrDefault(code, 0.0);
        }
    }

    static class TaxService {
        private static TaxService instance;
        private final HashMap<String, Double> rates = new HashMap<>();
        private TaxService() {
            rates.put("NY", 0.08);
            rates.put("CA", 0.0725);
        }
        static synchronized TaxService getInstance() {
            if (instance == null) instance = new TaxService();
            return instance;
        }
        double getRate(String region) {
            return rates.getOrDefault(region, 0.0);
        }
    }

    static class MutableOrder {
        ArrayList<double[]> items = new ArrayList<>();
        double total = 0;
        double discount = 0;
        double tax = 0;

        void addItem(String name, double price, int qty) {
            items.add(new double[]{price, qty});
            total += price * qty;
        }

        void applyCoupon(String code) {
            // Hidden dependency: reaches out to singleton
            double pct = CouponRegistry.getInstance().lookup(code);
            discount = total * pct;
            total -= discount;
        }

        void calcTax(String region) {
            // Hidden dependency: reaches out to singleton
            double rate = TaxService.getInstance().getRate(region);
            tax = total * rate;
            total += tax;
        }
    }

    /**
     * Crime: mutable methods with singleton lookups.
     * Returns [add_items, apply_coupon, calc_tax]
     */
    static long[] crimeRun() {
        long t0, t1;

        t0 = System.nanoTime();
        var order = new MutableOrder();
        order.addItem("Widget", 29.99, 2);
        order.addItem("Gadget", 39.99, 1);
        order.addItem("Doohickey", 19.99, 3);
        sink = Double.doubleToLongBits(order.total);
        t1 = System.nanoTime();
        long addItemNs = t1 - t0;

        t0 = System.nanoTime();
        order.applyCoupon("SAVE10");
        sink = Double.doubleToLongBits(order.discount);
        t1 = System.nanoTime();
        long applyCouponNs = t1 - t0;

        t0 = System.nanoTime();
        order.calcTax("NY");
        sink = Double.doubleToLongBits(order.total);
        t1 = System.nanoTime();
        long calcTaxNs = t1 - t0;

        return new long[]{ addItemNs, applyCouponNs, calcTaxNs };
    }

    // ═══════════════════════════════════════════════
    // RESCUE: pure functions — data in, data out
    // No singletons, no mutation, no hidden dependencies.
    // ═══════════════════════════════════════════════

    static double pureSubtotal(double[][] items) {
        double sum = 0;
        for (var item : items) sum += item[0] * item[1];
        return sum;
    }

    static double pureDiscount(double subtotal, double pct) {
        return subtotal * pct;
    }

    static double pureTax(double amount, double rate) {
        return amount * rate;
    }

    /**
     * Rescue: pure functions with all data as parameters.
     * Returns [subtotal, discount, tax]
     */
    static long[] rescueRun() {
        long t0, t1;
        double[][] items = {{29.99, 2}, {39.99, 1}, {19.99, 3}};

        t0 = System.nanoTime();
        double subtotal = pureSubtotal(items);
        sink = Double.doubleToLongBits(subtotal);
        t1 = System.nanoTime();
        long subtotalNs = t1 - t0;

        t0 = System.nanoTime();
        double disc = pureDiscount(subtotal, 0.10);
        double afterDiscount = subtotal - disc;
        sink = Double.doubleToLongBits(afterDiscount);
        t1 = System.nanoTime();
        long discountNs = t1 - t0;

        t0 = System.nanoTime();
        double tax = pureTax(afterDiscount, 0.08);
        double total = afterDiscount + tax;
        sink = Double.doubleToLongBits(total);
        t1 = System.nanoTime();
        long taxNs = t1 - t0;

        return new long[]{ subtotalNs, discountNs, taxNs };
    }

    // ═══════════════════════════════════════════════
    // HARNESS
    // ═══════════════════════════════════════════════

    static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    public static void main(String[] args) {
        // Force singleton initialization
        CouponRegistry.getInstance();
        TaxService.getInstance();

        for (int i = 0; i < WARMUP; i++) { crimeRun(); rescueRun(); }

        long[][] crimeResults = new long[RUNS][];
        long[][] rescueResults = new long[RUNS][];
        for (int i = 0; i < RUNS; i++) {
            crimeResults[i] = crimeRun();
            rescueResults[i] = rescueRun();
        }

        String[] crimeOps = {"add_items", "apply_coupon", "calc_tax"};
        long[] crimeMedians = new long[crimeOps.length];
        for (int op = 0; op < crimeOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = crimeResults[i][op];
            crimeMedians[op] = median(col);
        }

        String[] rescueOps = {"subtotal", "discount", "tax"};
        long[] rescueMedians = new long[rescueOps.length];
        for (int op = 0; op < rescueOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = rescueResults[i][op];
            rescueMedians[op] = median(col);
        }

        var os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        var runtime = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

        System.out.println("{");
        System.out.println("  \"chapter\": 4,");
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
