import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The Dishonest Order class.
 *
 * Every method mutates internal state. addItem() triggers a cascade of
 * recalculations that each reach into global singletons. The same sequence
 * of calls can produce different results depending on external cache state.
 *
 * Instrumented with System.nanoTime() to capture per-operation costs.
 */
public class Order {

    // -- Mutable fields (the "state web") --
    private final List<Item> items = new ArrayList<>();
    private double total = 0;
    private double discount = 0;
    private double tax = 0;
    private String couponCode = null;
    private Instant updatedAt = null;

    // -- Trace output --
    private final List<long[]> trace = new ArrayList<>();

    private void record(long start) {
        trace.add(new long[]{ System.nanoTime() - start });
    }

    // -- The crime scene --

    public void addItem(Item item) {
        long t0 = System.nanoTime();
        this.items.add(item);
        record(t0);

        recalculateTotal();
        recalculateDiscount();
        recalculateTax();

        t0 = System.nanoTime();
        this.updatedAt = Instant.now();
        record(t0);
    }

    public void applyCoupon(String code) {
        long t0 = System.nanoTime();
        this.couponCode = code;
        record(t0);

        recalculateDiscount();
        recalculateTax();

        t0 = System.nanoTime();
        this.updatedAt = Instant.now();
        record(t0);
    }

    private void recalculateTotal() {
        long t0 = System.nanoTime();
        this.total = items.stream().mapToDouble(Item::price).sum();
        record(t0);
    }

    private void recalculateDiscount() {
        long t0 = System.nanoTime();
        var registry = CouponRegistry.getInstance(); // singleton lookup
        record(t0);

        t0 = System.nanoTime();
        var coupon = registry.lookup(this.couponCode); // cache check
        this.discount = coupon != null ? coupon.apply(this.total) : 0;
        record(t0);
    }

    private void recalculateTax() {
        long t0 = System.nanoTime();
        var taxService = TaxService.getInstance(); // singleton lookup
        record(t0);

        t0 = System.nanoTime();
        double taxable = this.total - this.discount;
        this.tax = taxService.calculate("NY", taxable); // cache check
        record(t0);
    }

    public String traceJson() {
        var sb = new StringBuilder("[\n");
        for (int i = 0; i < trace.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append("  [").append(trace.get(i)[0]).append("]");
        }
        sb.append("\n]");
        return sb.toString();
    }

    // -- Supporting types --

    public record Item(String name, double price) {}

    // Simulated singletons
    static class CouponRegistry {
        private static CouponRegistry instance;
        static synchronized CouponRegistry getInstance() {
            if (instance == null) instance = new CouponRegistry();
            return instance;
        }
        Coupon lookup(String code) {
            if ("SAVE10".equals(code)) return new Coupon(0.10);
            return null;
        }
    }

    static class Coupon {
        final double rate;
        Coupon(double rate) { this.rate = rate; }
        double apply(double total) { return total * rate; }
    }

    static class TaxService {
        private static TaxService instance;
        static synchronized TaxService getInstance() {
            if (instance == null) instance = new TaxService();
            return instance;
        }
        double calculate(String region, double taxable) {
            return taxable * 0.08; // 8% NY tax
        }
    }

    // -- Main: run and dump trace --
    public static void main(String[] args) {
        var order = new Order();
        order.addItem(new Item("Widget", 29.99));
        order.addItem(new Item("Gadget", 39.99));
        order.addItem(new Item("Doohickey", 19.99));
        order.applyCoupon("SAVE10");
        System.out.println(order.traceJson());
    }
}
