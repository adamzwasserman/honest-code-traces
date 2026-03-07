import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Ch.9 harness: measures nanosecond cost of ORM-style object graph
 * traversal with cache lookup vs flat map-based data access (simulating
 * a single SQL JOIN result).
 *
 * Usage: javac Ch09Harness.java && java Ch09Harness
 */
public class Ch09Harness {

    static final int WARMUP = 5000;
    static final int RUNS   = 1000;

    static volatile long sink;

    // ═══════════════════════════════════════════════
    // CRIME: ORM + cache layer
    // Simulates: request → cache check → cache MISS → load Customer ORM object →
    // pointer chase through Orders → LineItems → Addresses →
    // assemble DTO → store in cache → return
    // ═══════════════════════════════════════════════

    // ORM-style entity classes (scattered heap objects)
    static class AddressEntity {
        final String street, city, zip;
        AddressEntity(String s, String c, String z) { street = s; city = c; zip = z; }
    }

    static class LineItemEntity {
        final String product;
        final double price;
        final int qty;
        LineItemEntity(String p, double pr, int q) { product = p; price = pr; qty = q; }
    }

    static class OrderEntity {
        final long id;
        final LineItemEntity[] items;
        final AddressEntity shippingAddress;
        OrderEntity(long id, LineItemEntity[] items, AddressEntity addr) {
            this.id = id; this.items = items; this.shippingAddress = addr;
        }
    }

    static class CustomerEntity {
        final String name, email;
        final OrderEntity[] orders;
        CustomerEntity(String name, String email, OrderEntity[] orders) {
            this.name = name; this.email = email; this.orders = orders;
        }
    }

    // Cache
    static HashMap<String, Map<String, Object>> cache = new HashMap<>();

    // Pre-built scattered entity graph
    static CustomerEntity customer;

    // Pre-built flat result (simulating SQL JOIN row)
    static Map<String, Object> flatResult;

    static ArrayList<Object> junk = new ArrayList<>();

    static void setup() {
        // Scatter allocations
        for (int i = 0; i < 100; i++) junk.add(new byte[512]);
        var addr1 = new AddressEntity("123 Main", "New York", "10001");
        for (int i = 0; i < 100; i++) junk.add(new byte[512]);
        var addr2 = new AddressEntity("456 Oak", "Brooklyn", "11201");
        for (int i = 0; i < 100; i++) junk.add(new byte[512]);
        var items1 = new LineItemEntity[]{
            new LineItemEntity("Widget", 29.99, 2),
            new LineItemEntity("Gadget", 39.99, 1)
        };
        for (int i = 0; i < 100; i++) junk.add(new byte[512]);
        var items2 = new LineItemEntity[]{
            new LineItemEntity("Doohickey", 19.99, 3)
        };
        for (int i = 0; i < 100; i++) junk.add(new byte[512]);
        var order1 = new OrderEntity(1001, items1, addr1);
        for (int i = 0; i < 100; i++) junk.add(new byte[512]);
        var order2 = new OrderEntity(1002, items2, addr2);
        for (int i = 0; i < 100; i++) junk.add(new byte[512]);
        customer = new CustomerEntity("Alice", "alice@example.com",
            new OrderEntity[]{ order1, order2 });

        // Flat JOIN result
        flatResult = Map.of(
            "name", "Alice",
            "email", "alice@example.com",
            "order_count", 2,
            "total", 119.96,
            "city", "New York"
        );
    }

    /**
     * Measure ORM + cache path step by step.
     * Returns [request, cache_check, cache_miss, load_customer, chase_orders,
     *          chase_items, chase_addresses, assemble_dto, store_cache, return_response]
     */
    static long[] crimeRun() {
        long t0, t1;
        cache.clear(); // ensure cache miss

        // 1. Request arrives
        t0 = System.nanoTime();
        String key = "customer:alice";
        sink = key.hashCode();
        t1 = System.nanoTime();
        long requestNs = t1 - t0;

        // 2. Check cache key
        t0 = System.nanoTime();
        var cached = cache.get(key);
        t1 = System.nanoTime();
        long cacheCheckNs = t1 - t0;

        // 3. Cache MISS — need to load from "database" (ORM)
        t0 = System.nanoTime();
        boolean miss = (cached == null);
        sink = miss ? 1 : 0;
        t1 = System.nanoTime();
        long cacheMissNs = t1 - t0;

        // 4. Load Customer object (root entity)
        t0 = System.nanoTime();
        sink = customer.name.length() + customer.email.length();
        t1 = System.nanoTime();
        long loadCustomerNs = t1 - t0;

        // 5. Pointer chase → Orders
        t0 = System.nanoTime();
        int orderCount = customer.orders.length;
        sink = orderCount;
        t1 = System.nanoTime();
        long chaseOrdersNs = t1 - t0;

        // 6. Pointer chase → LineItems (across all orders)
        t0 = System.nanoTime();
        double total = 0;
        for (var order : customer.orders) {
            for (var item : order.items) {
                total += item.price * item.qty;
            }
        }
        sink = Double.doubleToLongBits(total);
        t1 = System.nanoTime();
        long chaseItemsNs = t1 - t0;

        // 7. Pointer chase → Addresses
        t0 = System.nanoTime();
        for (var order : customer.orders) {
            sink = order.shippingAddress.city.length();
        }
        t1 = System.nanoTime();
        long chaseAddressesNs = t1 - t0;

        // 8. Assemble response DTO
        t0 = System.nanoTime();
        var dto = new HashMap<String, Object>();
        dto.put("name", customer.name);
        dto.put("email", customer.email);
        dto.put("order_count", orderCount);
        dto.put("total", total);
        t1 = System.nanoTime();
        long assembleDtoNs = t1 - t0;

        // 9. Store in cache
        t0 = System.nanoTime();
        cache.put(key, dto);
        t1 = System.nanoTime();
        long storeCacheNs = t1 - t0;

        // 10. Return response
        t0 = System.nanoTime();
        sink = dto.size();
        t1 = System.nanoTime();
        long returnNs = t1 - t0;

        return new long[]{ requestNs, cacheCheckNs, cacheMissNs, loadCustomerNs,
                           chaseOrdersNs, chaseItemsNs, chaseAddressesNs,
                           assembleDtoNs, storeCacheNs, returnNs };
    }

    // ═══════════════════════════════════════════════
    // RESCUE: flat SQL JOIN result
    // Single map read — no object graph, no cache.
    // ═══════════════════════════════════════════════

    /**
     * Measure flat query path.
     * Returns [request, query, return_response]
     */
    static long[] rescueRun() {
        long t0, t1;

        // 1. Request arrives
        t0 = System.nanoTime();
        sink = "customer:alice".hashCode();
        t1 = System.nanoTime();
        long requestNs = t1 - t0;

        // 2. SELECT ... JOIN ... (simulate reading flat result)
        t0 = System.nanoTime();
        sink = ((String) flatResult.get("name")).length();
        sink = ((String) flatResult.get("email")).length();
        sink = (int) flatResult.get("order_count");
        sink = Double.doubleToLongBits((double) flatResult.get("total"));
        sink = ((String) flatResult.get("city")).length();
        t1 = System.nanoTime();
        long queryNs = t1 - t0;

        // 3. Return dict
        t0 = System.nanoTime();
        sink = flatResult.size();
        t1 = System.nanoTime();
        long returnNs = t1 - t0;

        return new long[]{ requestNs, queryNs, returnNs };
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

        for (int i = 0; i < WARMUP; i++) { crimeRun(); rescueRun(); }

        long[][] crimeResults = new long[RUNS][];
        long[][] rescueResults = new long[RUNS][];
        for (int i = 0; i < RUNS; i++) {
            crimeResults[i] = crimeRun();
            rescueResults[i] = rescueRun();
        }

        String[] crimeOps = {"request", "cache_check", "cache_miss", "load_customer",
                             "chase_orders", "chase_items", "chase_addresses",
                             "assemble_dto", "store_cache", "return_response"};
        long[] crimeMedians = new long[crimeOps.length];
        for (int op = 0; op < crimeOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = crimeResults[i][op];
            crimeMedians[op] = median(col);
        }

        String[] rescueOps = {"request", "query", "return_response"};
        long[] rescueMedians = new long[rescueOps.length];
        for (int op = 0; op < rescueOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = rescueResults[i][op];
            rescueMedians[op] = median(col);
        }

        var os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        var runtime = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

        System.out.println("{");
        System.out.println("  \"chapter\": 9,");
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
