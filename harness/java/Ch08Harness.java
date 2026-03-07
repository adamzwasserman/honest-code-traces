import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/**
 * Ch.8 harness: measures nanosecond cost of defensive error handling
 * (try/catch + wrapping + retry + logging) vs let-it-crash (simple
 * exception throw + supervisor restart).
 *
 * Usage: javac Ch08Harness.java && java Ch08Harness
 */
public class Ch08Harness {

    static final int WARMUP = 5000;
    static final int RUNS   = 1000;

    static volatile long sink;

    // ═══════════════════════════════════════════════
    // CRIME: defensive error handling — 6 address spaces
    // try/catch → wrap exception → check retry counter →
    // circuit breaker check → log error → dead letter queue
    // ═══════════════════════════════════════════════

    // Simulated external state stores
    static HashMap<String, Integer> retryCounters = new HashMap<>();
    static HashMap<String, Boolean> circuitBreakers = new HashMap<>();
    static ArrayList<String> errorLog = new ArrayList<>();
    static ArrayList<Map<String, Object>> deadLetterQueue = new ArrayList<>();

    static void setupCrime() {
        retryCounters.put("order-service", 0);
        circuitBreakers.put("order-service", false); // not tripped
        errorLog.clear();
        deadLetterQueue.clear();
    }

    /**
     * Crime: full defensive error recovery path.
     * Returns [try_catch, wrap_exception, check_retry, circuit_breaker,
     *          log_error, dead_letter, total_recovery]
     */
    static long[] crimeRun() {
        long t0, t1;
        setupCrime();

        // 1. Try/catch block overhead
        t0 = System.nanoTime();
        Exception caught = null;
        try {
            // Simulate operation that fails
            if (true) throw new RuntimeException("Connection timeout");
        } catch (RuntimeException e) {
            caught = e;
            sink = e.getMessage().length();
        }
        t1 = System.nanoTime();
        long tryCatchNs = t1 - t0;

        // 2. Wrap exception with context
        t0 = System.nanoTime();
        var wrapped = new RuntimeException("OrderService.processOrder failed", caught);
        sink = wrapped.getMessage().length();
        t1 = System.nanoTime();
        long wrapNs = t1 - t0;

        // 3. Check retry counter (simulating Redis/external state)
        t0 = System.nanoTime();
        int retries = retryCounters.get("order-service");
        retries++;
        retryCounters.put("order-service", retries);
        boolean shouldRetry = retries < 3;
        sink = shouldRetry ? 1 : 0;
        t1 = System.nanoTime();
        long retryNs = t1 - t0;

        // 4. Circuit breaker check
        t0 = System.nanoTime();
        boolean tripped = circuitBreakers.get("order-service");
        if (retries >= 3) {
            circuitBreakers.put("order-service", true);
            tripped = true;
        }
        sink = tripped ? 1 : 0;
        t1 = System.nanoTime();
        long circuitNs = t1 - t0;

        // 5. Log error (simulating filesystem write)
        t0 = System.nanoTime();
        errorLog.add("[ERROR] " + wrapped.getMessage() + " attempt=" + retries);
        sink = errorLog.size();
        t1 = System.nanoTime();
        long logNs = t1 - t0;

        // 6. Dead letter queue (simulating message broker)
        t0 = System.nanoTime();
        var dlqEntry = new HashMap<String, Object>();
        dlqEntry.put("error", wrapped.getMessage());
        dlqEntry.put("retries", retries);
        dlqEntry.put("timestamp", System.currentTimeMillis());
        deadLetterQueue.add(dlqEntry);
        sink = deadLetterQueue.size();
        t1 = System.nanoTime();
        long dlqNs = t1 - t0;

        return new long[]{ tryCatchNs, wrapNs, retryNs, circuitNs, logNs, dlqNs };
    }

    // ═══════════════════════════════════════════════
    // RESCUE: let it crash — supervisor restarts
    // Process crashes → supervisor detects → restart
    // ═══════════════════════════════════════════════

    static volatile boolean processAlive = true;

    /**
     * Rescue: crash and restart.
     * Returns [crash, detect, restart]
     */
    static long[] rescueRun() {
        long t0, t1;

        // 1. Process crashes (just throw — no catch, no wrapping)
        t0 = System.nanoTime();
        processAlive = false;
        sink = 0; // process is dead
        t1 = System.nanoTime();
        long crashNs = t1 - t0;

        // 2. Supervisor detects crash (check boolean flag)
        t0 = System.nanoTime();
        boolean dead = !processAlive;
        sink = dead ? 1 : 0;
        t1 = System.nanoTime();
        long detectNs = t1 - t0;

        // 3. Restart with clean state (new process = new HashMap)
        t0 = System.nanoTime();
        processAlive = true;
        var freshState = new HashMap<String, Object>();
        freshState.put("status", "ready");
        sink = freshState.size();
        t1 = System.nanoTime();
        long restartNs = t1 - t0;

        return new long[]{ crashNs, detectNs, restartNs };
    }

    // ═══════════════════════════════════════════════
    // HARNESS
    // ═══════════════════════════════════════════════

    static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    public static void main(String[] args) {
        setupCrime();

        for (int i = 0; i < WARMUP; i++) { crimeRun(); rescueRun(); }

        long[][] crimeResults = new long[RUNS][];
        long[][] rescueResults = new long[RUNS][];
        for (int i = 0; i < RUNS; i++) {
            crimeResults[i] = crimeRun();
            rescueResults[i] = rescueRun();
        }

        String[] crimeOps = {"try_catch", "wrap_exception", "check_retry",
                             "circuit_breaker", "log_error", "dead_letter"};
        long[] crimeMedians = new long[crimeOps.length];
        for (int op = 0; op < crimeOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = crimeResults[i][op];
            crimeMedians[op] = median(col);
        }

        String[] rescueOps = {"crash", "detect", "restart"};
        long[] rescueMedians = new long[rescueOps.length];
        for (int op = 0; op < rescueOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = rescueResults[i][op];
            rescueMedians[op] = median(col);
        }

        var os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        var runtime = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

        System.out.println("{");
        System.out.println("  \"chapter\": 8,");
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
