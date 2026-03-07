import java.util.Arrays;
import java.util.ArrayList;

/**
 * Ch.3 harness: measures nanosecond cost of pointer chasing through
 * scattered heap objects vs reading contiguous flat data.
 *
 * Usage: javac Ch03Harness.java && java Ch03Harness
 */
public class Ch03Harness {

    static final int WARMUP = 5000;
    static final int RUNS   = 1000;

    static volatile long sink;

    // ═══════════════════════════════════════════════
    // CRIME: scattered objects with pointer chasing
    // Order → Customer → Address → TaxRegion → RateTable → LineItem[]
    // Deliberately scatter by allocating junk between each object.
    // ═══════════════════════════════════════════════

    static class LineItem {
        final String name;
        final double price;
        final int qty;
        LineItem(String name, double price, int qty) {
            this.name = name; this.price = price; this.qty = qty;
        }
    }

    static class RateTable {
        final double rate;
        final String region;
        final LineItem[] items;
        RateTable(double rate, String region, LineItem[] items) {
            this.rate = rate; this.region = region; this.items = items;
        }
    }

    static class TaxRegion {
        final String code;
        final RateTable rateTable;
        TaxRegion(String code, RateTable rateTable) {
            this.code = code; this.rateTable = rateTable;
        }
    }

    static class Address {
        final String street;
        final String zip;
        final TaxRegion taxRegion;
        Address(String street, String zip, TaxRegion taxRegion) {
            this.street = street; this.zip = zip; this.taxRegion = taxRegion;
        }
    }

    static class Customer {
        final String name;
        final String email;
        final Address address;
        Customer(String name, String email, Address address) {
            this.name = name; this.email = email; this.address = address;
        }
    }

    static class Order {
        final long id;
        final Customer customer;
        Order(long id, Customer customer) {
            this.id = id; this.customer = customer;
        }
    }

    // ═══════════════════════════════════════════════
    // RESCUE: single flat record with all data inline
    // ═══════════════════════════════════════════════

    record FlatOrder(
        long orderId,
        String customerName, String customerEmail,
        String street, String zip,
        String taxCode, double taxRate,
        String item1Name, double item1Price, int item1Qty,
        String item2Name, double item2Price, int item2Qty,
        String item3Name, double item3Price, int item3Qty
    ) {}

    // Allocate junk to scatter objects in memory
    static ArrayList<Object> junk = new ArrayList<>();
    static void scatter() {
        for (int i = 0; i < 200; i++) junk.add(new byte[1024]);
    }

    static Order scatteredOrder;
    static FlatOrder flatOrder;

    static void setup() {
        // Build scattered object graph with junk between each allocation
        scatter();
        var items = new LineItem[]{
            new LineItem("Widget", 29.99, 2),
            new LineItem("Gadget", 39.99, 1),
            new LineItem("Doohickey", 19.99, 3)
        };
        scatter();
        var rateTable = new RateTable(0.08, "NY", items);
        scatter();
        var taxRegion = new TaxRegion("NY-001", rateTable);
        scatter();
        var address = new Address("123 Main St", "10001", taxRegion);
        scatter();
        var customer = new Customer("Alice", "alice@example.com", address);
        scatter();
        scatteredOrder = new Order(1001, customer);

        // Build flat equivalent
        flatOrder = new FlatOrder(
            1001, "Alice", "alice@example.com",
            "123 Main St", "10001",
            "NY-001", 0.08,
            "Widget", 29.99, 2,
            "Gadget", 39.99, 1,
            "Doohickey", 19.99, 3
        );
    }

    /** Measure each pointer hop individually. Returns [order, customer, address, taxRegion, rateTable, lineItems]. */
    static long[] crimeRun() {
        long t0, t1;
        var o = scatteredOrder;

        // Hop 1: read Order fields
        t0 = System.nanoTime();
        sink = o.id;
        t1 = System.nanoTime();
        long orderNs = t1 - t0;

        // Hop 2: Order → Customer (pointer dereference)
        t0 = System.nanoTime();
        sink = o.customer.name.length() + o.customer.email.length();
        t1 = System.nanoTime();
        long customerNs = t1 - t0;

        // Hop 3: Customer → Address
        t0 = System.nanoTime();
        sink = o.customer.address.street.length() + o.customer.address.zip.hashCode();
        t1 = System.nanoTime();
        long addressNs = t1 - t0;

        // Hop 4: Address → TaxRegion
        t0 = System.nanoTime();
        sink = o.customer.address.taxRegion.code.length();
        t1 = System.nanoTime();
        long taxRegionNs = t1 - t0;

        // Hop 5: TaxRegion → RateTable
        t0 = System.nanoTime();
        double rate = o.customer.address.taxRegion.rateTable.rate;
        sink = Double.doubleToLongBits(rate);
        t1 = System.nanoTime();
        long rateTableNs = t1 - t0;

        // Hop 6: RateTable → LineItem[] (array + 3 element accesses)
        t0 = System.nanoTime();
        var items = o.customer.address.taxRegion.rateTable.items;
        double total = 0;
        for (var item : items) total += item.price * item.qty;
        sink = Double.doubleToLongBits(total);
        t1 = System.nanoTime();
        long itemsNs = t1 - t0;

        return new long[]{ orderNs, customerNs, addressNs, taxRegionNs, rateTableNs, itemsNs };
    }

    /** Measure single flat read. Returns [flatReadNs]. */
    static long[] rescueRun() {
        long t0, t1;
        var f = flatOrder;

        t0 = System.nanoTime();
        sink = f.orderId();
        sink = f.customerName().length() + f.customerEmail().length();
        sink = f.street().length() + f.zip().hashCode();
        sink = f.taxCode().length();
        sink = Double.doubleToLongBits(f.taxRate());
        double total = f.item1Price() * f.item1Qty()
                     + f.item2Price() * f.item2Qty()
                     + f.item3Price() * f.item3Qty();
        sink = Double.doubleToLongBits(total);
        t1 = System.nanoTime();
        long flatNs = t1 - t0;

        return new long[]{ flatNs };
    }

    // ═══════════════════════════════════════════════
    // HARNESS
    // ═══════════════════════════════════════════════

    static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    public static void main(String[] args) {
        setup();

        // Warmup
        for (int i = 0; i < WARMUP; i++) { crimeRun(); rescueRun(); }

        // Collect
        long[][] crimeResults = new long[RUNS][];
        long[][] rescueResults = new long[RUNS][];
        for (int i = 0; i < RUNS; i++) {
            crimeResults[i] = crimeRun();
            rescueResults[i] = rescueRun();
        }

        String[] crimeOps = {"order", "customer", "address", "tax_region", "rate_table", "line_items"};
        long[] crimeMedians = new long[6];
        for (int op = 0; op < 6; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = crimeResults[i][op];
            crimeMedians[op] = median(col);
        }

        long[] rescueCol = new long[RUNS];
        for (int i = 0; i < RUNS; i++) rescueCol[i] = rescueResults[i][0];
        long rescueMedian = median(rescueCol);

        var os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        var runtime = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

        System.out.println("{");
        System.out.println("  \"chapter\": 3,");
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
        System.out.println("    \"flat_read\": " + rescueMedian);
        System.out.println("  }");
        System.out.println("}");
    }
}
