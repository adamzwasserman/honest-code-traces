import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ch.5 harness: measures nanosecond cost of reading/writing state
 * scattered across multiple locations (Redux-style) vs single source
 * of truth (server-rendered DOM / single dict).
 *
 * Usage: javac Ch05Harness.java && java Ch05Harness
 */
public class Ch05Harness {

    static final int WARMUP = 5000;
    static final int RUNS   = 1000;

    static volatile long sink;

    // ═══════════════════════════════════════════════
    // CRIME: state scattered across 6 locations
    // Simulates: Redux store, component state, form input,
    // URL params, localStorage, derived/memoized cache
    // ═══════════════════════════════════════════════

    // Simulate 6 separate state stores
    static HashMap<String, Object> reduxStore = new HashMap<>();
    static HashMap<String, Object> componentState = new HashMap<>();
    static HashMap<String, Object> formInput = new HashMap<>();
    static HashMap<String, Object> urlParams = new HashMap<>();
    static HashMap<String, Object> localStorage = new HashMap<>();
    static HashMap<String, Object> derivedCache = new HashMap<>();

    static void setupCrime() {
        String email = "alice@example.com";
        reduxStore.put("email", email);
        componentState.put("email", email);
        formInput.put("email", email);
        urlParams.put("email", email);
        localStorage.put("email", email);
        derivedCache.put("email", email);
    }

    /**
     * Crime: update email across all 6 locations (propagation).
     * Returns [write_redux, write_component, write_form, write_url,
     *          write_local, write_derived, read_all_verify]
     */
    static long[] crimeRun() {
        long t0, t1;
        String newEmail = "alice@newdomain.com";

        // 1. Write to Redux store
        t0 = System.nanoTime();
        reduxStore.put("email", newEmail);
        sink = ((String) reduxStore.get("email")).length();
        t1 = System.nanoTime();
        long writeRedux = t1 - t0;

        // 2. Propagate to component state
        t0 = System.nanoTime();
        componentState.put("email", reduxStore.get("email"));
        sink = ((String) componentState.get("email")).length();
        t1 = System.nanoTime();
        long writeComponent = t1 - t0;

        // 3. Propagate to form input
        t0 = System.nanoTime();
        formInput.put("email", reduxStore.get("email"));
        sink = ((String) formInput.get("email")).length();
        t1 = System.nanoTime();
        long writeForm = t1 - t0;

        // 4. Propagate to URL params
        t0 = System.nanoTime();
        urlParams.put("email", reduxStore.get("email"));
        sink = ((String) urlParams.get("email")).length();
        t1 = System.nanoTime();
        long writeUrl = t1 - t0;

        // 5. Propagate to localStorage
        t0 = System.nanoTime();
        localStorage.put("email", reduxStore.get("email"));
        sink = ((String) localStorage.get("email")).length();
        t1 = System.nanoTime();
        long writeLocal = t1 - t0;

        // 6. Invalidate and recompute derived cache
        t0 = System.nanoTime();
        derivedCache.put("email", reduxStore.get("email"));
        sink = ((String) derivedCache.get("email")).length();
        t1 = System.nanoTime();
        long writeDerived = t1 - t0;

        // 7. Read from all locations to verify consistency
        t0 = System.nanoTime();
        sink = ((String) reduxStore.get("email")).length();
        sink += ((String) componentState.get("email")).length();
        sink += ((String) formInput.get("email")).length();
        sink += ((String) urlParams.get("email")).length();
        sink += ((String) localStorage.get("email")).length();
        sink += ((String) derivedCache.get("email")).length();
        t1 = System.nanoTime();
        long readAll = t1 - t0;

        // Reset for next run
        setupCrime();

        return new long[]{ writeRedux, writeComponent, writeForm, writeUrl,
                           writeLocal, writeDerived, readAll };
    }

    // ═══════════════════════════════════════════════
    // RESCUE: single source of truth
    // ═══════════════════════════════════════════════

    static HashMap<String, Object> singleSource = new HashMap<>();

    static void setupRescue() {
        singleSource.put("email", "alice@example.com");
    }

    /**
     * Rescue: update email in one place, read from one place.
     * Returns [write, read]
     */
    static long[] rescueRun() {
        long t0, t1;

        // 1. Write to single source
        t0 = System.nanoTime();
        singleSource.put("email", "alice@newdomain.com");
        sink = ((String) singleSource.get("email")).length();
        t1 = System.nanoTime();
        long writeNs = t1 - t0;

        // 2. Read from single source
        t0 = System.nanoTime();
        sink = ((String) singleSource.get("email")).length();
        t1 = System.nanoTime();
        long readNs = t1 - t0;

        // Reset
        setupRescue();

        return new long[]{ writeNs, readNs };
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
        setupRescue();

        for (int i = 0; i < WARMUP; i++) { crimeRun(); rescueRun(); }

        long[][] crimeResults = new long[RUNS][];
        long[][] rescueResults = new long[RUNS][];
        for (int i = 0; i < RUNS; i++) {
            crimeResults[i] = crimeRun();
            rescueResults[i] = rescueRun();
        }

        String[] crimeOps = {"write_redux", "write_component", "write_form",
                             "write_url", "write_local", "write_derived", "read_all_verify"};
        long[] crimeMedians = new long[crimeOps.length];
        for (int op = 0; op < crimeOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = crimeResults[i][op];
            crimeMedians[op] = median(col);
        }

        String[] rescueOps = {"write", "read"};
        long[] rescueMedians = new long[rescueOps.length];
        for (int op = 0; op < rescueOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = rescueResults[i][op];
            rescueMedians[op] = median(col);
        }

        var os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        var runtime = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

        System.out.println("{");
        System.out.println("  \"chapter\": 5,");
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
