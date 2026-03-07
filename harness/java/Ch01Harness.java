import java.util.Arrays;

/**
 * Ch.1 harness: measures nanosecond cost of deep call stacks (Spring Boot-style
 * 13-frame middleware chain) vs flat pipeline (4 direct function calls).
 *
 * Usage: javac Ch01Harness.java && java Ch01Harness
 */
public class Ch01Harness {

    static final int WARMUP = 5000;
    static final int RUNS   = 1000;

    // Sink to prevent dead code elimination
    static volatile long sink;

    // ═══════════════════════════════════════════════
    // CRIME: 13-level Spring Boot-style middleware chain
    // Each layer does a small check (like real middleware) before calling the next.
    // ═══════════════════════════════════════════════

    static String userData = "user-42";
    static String region = "us-east-1";

    static long entityManagerFind(String id) {
        // Simulate JPA EntityManager.find() — hash lookup
        long t0 = System.nanoTime();
        sink = id.hashCode() + 42;
        return System.nanoTime() - t0;
    }

    static long repositoryFindById(String id) {
        long t0 = System.nanoTime();
        sink = id.length(); // param check
        long self = System.nanoTime() - t0;
        return self + entityManagerFind(id);
    }

    static long serviceFindById(String id) {
        long t0 = System.nanoTime();
        if (id == null) throw new IllegalArgumentException();
        long self = System.nanoTime() - t0;
        return self + repositoryFindById(id);
    }

    static long controllerGetUser(String id) {
        long t0 = System.nanoTime();
        sink = id.charAt(0); // minimal work
        long self = System.nanoTime() - t0;
        return self + serviceFindById(id);
    }

    static long exceptionResolver(String id) {
        long t0 = System.nanoTime();
        // Exception handler resolver — wraps call, checks for exceptions
        long self = System.nanoTime() - t0;
        return self + controllerGetUser(id);
    }

    static long aopProxy(String id) {
        long t0 = System.nanoTime();
        // CGLIB proxy — method interception overhead
        sink = id.hashCode();
        long self = System.nanoTime() - t0;
        return self + exceptionResolver(id);
    }

    static long transactionInterceptor(String id) {
        long t0 = System.nanoTime();
        // Begin/commit transaction boundary check
        boolean readOnly = true;
        sink = readOnly ? 1 : 0;
        long self = System.nanoTime() - t0;
        return self + aopProxy(id);
    }

    static long handlerAdapter(String id) {
        long t0 = System.nanoTime();
        // Resolve handler method arguments
        sink = id.getBytes().length;
        long self = System.nanoTime() - t0;
        return self + transactionInterceptor(id);
    }

    static long requestMappingHandler(String id) {
        long t0 = System.nanoTime();
        // URL pattern matching
        boolean matches = "/api/users".startsWith("/api");
        sink = matches ? 1 : 0;
        long self = System.nanoTime() - t0;
        return self + handlerAdapter(id);
    }

    static long corsFilter(String id) {
        long t0 = System.nanoTime();
        // Check CORS headers
        String origin = "https://example.com";
        sink = origin.length();
        long self = System.nanoTime() - t0;
        return self + requestMappingHandler(id);
    }

    static long securityFilter(String id) {
        long t0 = System.nanoTime();
        // Authentication check — token validation
        String token = "Bearer eyJ...";
        boolean valid = token.startsWith("Bearer");
        sink = valid ? 1 : 0;
        long self = System.nanoTime() - t0;
        return self + corsFilter(id);
    }

    static long filterChain(String id) {
        long t0 = System.nanoTime();
        // Servlet filter chain dispatch
        sink = Thread.currentThread().getId();
        long self = System.nanoTime() - t0;
        return self + securityFilter(id);
    }

    static long servletDispatch(String id) {
        long t0 = System.nanoTime();
        // DispatcherServlet.doDispatch — top of the chain
        String method = "GET";
        sink = method.hashCode();
        long self = System.nanoTime() - t0;
        return self + filterChain(id);
    }

    /** Measure per-frame cost through 13-level stack. Returns total ns. */
    static long crimeRun() {
        return servletDispatch(userData);
    }

    // ═══════════════════════════════════════════════
    // RESCUE: 4-function flat pipeline
    // Same work, no nesting.
    // ═══════════════════════════════════════════════

    static long[] rescueRun() {
        long t0, t1;

        // handle_request: parse and validate route
        t0 = System.nanoTime();
        String method = "GET";
        String path = "/api/users/42";
        sink = method.hashCode() + path.hashCode();
        t1 = System.nanoTime();
        long handleNs = t1 - t0;

        // validate: check params
        t0 = System.nanoTime();
        String userId = "user-42";
        if (userId == null || userId.isEmpty()) throw new IllegalArgumentException();
        sink = userId.length();
        t1 = System.nanoTime();
        long validateNs = t1 - t0;

        // query_user: direct data access (same as entityManager)
        t0 = System.nanoTime();
        sink = userId.hashCode() + 42;
        t1 = System.nanoTime();
        long queryNs = t1 - t0;

        // respond: format result
        t0 = System.nanoTime();
        sink = 200;
        t1 = System.nanoTime();
        long respondNs = t1 - t0;

        return new long[]{ handleNs, validateNs, queryNs, respondNs };
    }

    // ═══════════════════════════════════════════════
    // HARNESS
    // ═══════════════════════════════════════════════

    static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    public static void main(String[] args) {
        // Warmup
        for (int i = 0; i < WARMUP; i++) { crimeRun(); rescueRun(); }

        // Collect
        long[] crimeResults = new long[RUNS];
        long[][] rescueResults = new long[RUNS][];
        for (int i = 0; i < RUNS; i++) {
            crimeResults[i] = crimeRun();
            rescueResults[i] = rescueRun();
        }

        long crimeMedian = median(crimeResults);

        String[] rescueOps = {"handle_request", "validate", "query_user", "respond"};
        long[] rescueMedians = new long[4];
        for (int op = 0; op < 4; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = rescueResults[i][op];
            rescueMedians[op] = median(col);
        }

        var os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        var runtime = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

        System.out.println("{");
        System.out.println("  \"chapter\": 1,");
        System.out.println("  \"language\": \"java\",");
        System.out.println("  \"os\": \"" + os + "\",");
        System.out.println("  \"runtime\": \"" + runtime + "\",");
        System.out.println("  \"runs\": " + RUNS + ",");
        System.out.println("  \"crime\": {");
        System.out.println("    \"total_13_frames\": " + crimeMedian);
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
