import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Ch.10 harness: measures nanosecond cost of mock-heavy test setup
 * (class-based testing with dependency injection) vs pure function
 * testing (direct call + assert).
 *
 * Usage: javac Ch10Harness.java && java Ch10Harness
 */
public class Ch10Harness {

    static final int WARMUP = 5000;
    static final int RUNS   = 1000;

    static volatile long sink;

    // ═══════════════════════════════════════════════
    // CRIME: class-based test with mocks
    // setUp → create mock registry → create mock tax service →
    // create mock logger → create container → inject dependencies →
    // create order → call method → verify mock interactions → tearDown
    // ═══════════════════════════════════════════════

    // Mock objects — simulating what Mockito/mock frameworks create
    interface CouponRegistry {
        double lookup(String code);
    }

    interface TaxService {
        double getRate(String region);
    }

    interface Logger {
        void log(String msg);
    }

    static class Container {
        HashMap<String, Object> services = new HashMap<>();
        void register(String name, Object svc) { services.put(name, svc); }
        Object resolve(String name) { return services.get(name); }
    }

    static class OrderService {
        CouponRegistry coupons;
        TaxService tax;
        Logger logger;

        OrderService(CouponRegistry c, TaxService t, Logger l) {
            coupons = c; tax = t; logger = l;
        }

        double processOrder(double subtotal, String coupon, String region) {
            double discount = coupons.lookup(coupon);
            double afterDiscount = subtotal - discount;
            double taxRate = tax.getRate(region);
            double total = afterDiscount * (1 + taxRate);
            logger.log("Order processed: " + total);
            return total;
        }
    }

    /**
     * Crime: full test lifecycle with mocks.
     * Returns [setUp, mock_registry, mock_tax, mock_logger,
     *          container, inject, create_order, call_method,
     *          verify_mocks, tearDown]
     */
    static long[] crimeRun() {
        long t0, t1;

        // 1. setUp — initialize test context
        t0 = System.nanoTime();
        var testContext = new HashMap<String, Object>();
        testContext.put("test_name", "testProcessOrder");
        sink = testContext.size();
        t1 = System.nanoTime();
        long setUpNs = t1 - t0;

        // 2. Create mock CouponRegistry
        t0 = System.nanoTime();
        CouponRegistry mockRegistry = (code) -> {
            sink = code.length();
            return 10.0; // stubbed response
        };
        sink = mockRegistry.lookup("SAVE10") > 0 ? 1 : 0;
        t1 = System.nanoTime();
        long mockRegistryNs = t1 - t0;

        // 3. Create mock TaxService
        t0 = System.nanoTime();
        TaxService mockTax = (region) -> {
            sink = region.length();
            return 0.08; // stubbed response
        };
        sink = Double.doubleToLongBits(mockTax.getRate("NY"));
        t1 = System.nanoTime();
        long mockTaxNs = t1 - t0;

        // 4. Create mock Logger
        t0 = System.nanoTime();
        ArrayList<String> logCapture = new ArrayList<>();
        Logger mockLogger = (msg) -> {
            logCapture.add(msg);
            sink = msg.length();
        };
        mockLogger.log("test");
        t1 = System.nanoTime();
        long mockLoggerNs = t1 - t0;

        // 5. Create DI container
        t0 = System.nanoTime();
        var container = new Container();
        container.register("couponRegistry", mockRegistry);
        container.register("taxService", mockTax);
        container.register("logger", mockLogger);
        sink = container.services.size();
        t1 = System.nanoTime();
        long containerNs = t1 - t0;

        // 6. Inject dependencies
        t0 = System.nanoTime();
        var resolvedRegistry = (CouponRegistry) container.resolve("couponRegistry");
        var resolvedTax = (TaxService) container.resolve("taxService");
        var resolvedLogger = (Logger) container.resolve("logger");
        sink = (resolvedRegistry != null ? 1 : 0) + (resolvedTax != null ? 1 : 0);
        t1 = System.nanoTime();
        long injectNs = t1 - t0;

        // 7. Create order service with injected deps
        t0 = System.nanoTime();
        var orderService = new OrderService(resolvedRegistry, resolvedTax, resolvedLogger);
        sink = orderService.coupons != null ? 1 : 0;
        t1 = System.nanoTime();
        long createOrderNs = t1 - t0;

        // 8. Call method under test
        t0 = System.nanoTime();
        logCapture.clear();
        double result = orderService.processOrder(100.0, "SAVE10", "NY");
        sink = Double.doubleToLongBits(result);
        t1 = System.nanoTime();
        long callMethodNs = t1 - t0;

        // 9. Verify mock interactions
        t0 = System.nanoTime();
        boolean logCalled = !logCapture.isEmpty();
        boolean resultCorrect = Math.abs(result - 97.2) < 0.01;
        sink = (logCalled ? 1 : 0) + (resultCorrect ? 1 : 0);
        t1 = System.nanoTime();
        long verifyNs = t1 - t0;

        // 10. tearDown
        t0 = System.nanoTime();
        testContext.clear();
        logCapture.clear();
        sink = testContext.size();
        t1 = System.nanoTime();
        long tearDownNs = t1 - t0;

        return new long[]{ setUpNs, mockRegistryNs, mockTaxNs, mockLoggerNs,
                           containerNs, injectNs, createOrderNs, callMethodNs,
                           verifyNs, tearDownNs };
    }

    // ═══════════════════════════════════════════════
    // RESCUE: pure function test — direct call + assert
    // ═══════════════════════════════════════════════

    // Pure function — no dependencies to mock
    static Map<String, Object> calculateOrder(double subtotal, double discount, double taxRate) {
        double afterDiscount = subtotal - discount;
        double tax = afterDiscount * taxRate;
        double total = afterDiscount + tax;
        return Map.of("subtotal", subtotal, "discount", discount,
                       "tax", tax, "total", total);
    }

    /**
     * Rescue: pure function test.
     * Returns [call, assert]
     */
    static long[] rescueRun() {
        long t0, t1;

        // 1. Call pure function directly
        t0 = System.nanoTime();
        var result = calculateOrder(100.0, 10.0, 0.08);
        sink = Double.doubleToLongBits((double) result.get("total"));
        t1 = System.nanoTime();
        long callNs = t1 - t0;

        // 2. Assert result
        t0 = System.nanoTime();
        double total = (double) result.get("total");
        boolean pass = Math.abs(total - 97.2) < 0.01;
        sink = pass ? 1 : 0;
        t1 = System.nanoTime();
        long assertNs = t1 - t0;

        return new long[]{ callNs, assertNs };
    }

    // ═══════════════════════════════════════════════
    // HARNESS
    // ═══════════════════════════════════════════════

    static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    public static void main(String[] args) {
        for (int i = 0; i < WARMUP; i++) { crimeRun(); rescueRun(); }

        long[][] crimeResults = new long[RUNS][];
        long[][] rescueResults = new long[RUNS][];
        for (int i = 0; i < RUNS; i++) {
            crimeResults[i] = crimeRun();
            rescueResults[i] = rescueRun();
        }

        String[] crimeOps = {"setUp", "mock_registry", "mock_tax", "mock_logger",
                             "container", "inject", "create_order", "call_method",
                             "verify_mocks", "tearDown"};
        long[] crimeMedians = new long[crimeOps.length];
        for (int op = 0; op < crimeOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = crimeResults[i][op];
            crimeMedians[op] = median(col);
        }

        String[] rescueOps = {"call", "assert"};
        long[] rescueMedians = new long[rescueOps.length];
        for (int op = 0; op < rescueOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = rescueResults[i][op];
            rescueMedians[op] = median(col);
        }

        var os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        var runtime = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

        System.out.println("{");
        System.out.println("  \"chapter\": 10,");
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
