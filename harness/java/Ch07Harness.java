import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Ch.7 harness: measures nanosecond cost of deep inheritance chain
 * with super() calls (4-level class hierarchy, each level allocates
 * framework state) vs flat function pipeline (functions mutate a
 * shared context dict).
 *
 * Usage: javac Ch07Harness.java && java Ch07Harness
 */
public class Ch07Harness {

    static final int WARMUP = 5000;
    static final int RUNS   = 1000;

    static volatile long sink;

    // ═══════════════════════════════════════════════
    // CRIME: 4-level inheritance chain
    // Each level has constructor overhead (super() chain) and
    // framework-style state allocation (HashMap per level).
    // Render dispatches up and down the chain.
    // ═══════════════════════════════════════════════

    static class BaseComponent {
        HashMap<String, Object> baseState = new HashMap<>();
        BaseComponent() {
            baseState.put("lifecycle", "created");
            baseState.put("rendered", false);
        }
        String render(Map<String, Object> ctx) {
            baseState.put("rendered", true);
            sink = baseState.size();
            return "base";
        }
    }

    static class AuthComponent extends BaseComponent {
        HashMap<String, Object> authState = new HashMap<>();
        AuthComponent() {
            super();
            authState.put("authenticated", false);
            authState.put("token", null);
            authState.put("permissions", new String[]{"read"});
        }
        String render(Map<String, Object> ctx) {
            String token = (String) ctx.get("token");
            authState.put("authenticated", token != null && token.startsWith("Bearer"));
            authState.put("token", token);
            sink = authState.size();
            return super.render(ctx);
        }
    }

    static class DataLoader extends AuthComponent {
        HashMap<String, Object> loaderState = new HashMap<>();
        DataLoader() {
            super();
            loaderState.put("loading", false);
            loaderState.put("data", null);
            loaderState.put("error", null);
            loaderState.put("retries", 0);
        }
        String render(Map<String, Object> ctx) {
            loaderState.put("loading", true);
            loaderState.put("data", ctx.get("user_id"));
            loaderState.put("loading", false);
            sink = loaderState.size();
            return super.render(ctx);
        }
    }

    static class UserProfilePage extends DataLoader {
        HashMap<String, Object> pageState = new HashMap<>();
        UserProfilePage() {
            super();
            pageState.put("title", "User Profile");
            pageState.put("breadcrumbs", new String[]{"Home", "Users", "Profile"});
            pageState.put("activeTab", "overview");
        }
        String render(Map<String, Object> ctx) {
            pageState.put("activeTab", ctx.getOrDefault("tab", "overview"));
            sink = pageState.size();
            return super.render(ctx);
        }
    }

    static HashMap<String, Object> testCtx;

    static void setup() {
        testCtx = new HashMap<>();
        testCtx.put("token", "Bearer eyJ...");
        testCtx.put("user_id", "user-42");
        testCtx.put("tab", "settings");
    }

    /**
     * Crime: construct + render through 4-level chain.
     * Returns [construct, render]
     */
    static long[] crimeRun() {
        long t0, t1;

        t0 = System.nanoTime();
        var p = new UserProfilePage();
        sink = p.baseState.size() + p.authState.size() + p.loaderState.size() + p.pageState.size();
        t1 = System.nanoTime();
        long constructNs = t1 - t0;

        t0 = System.nanoTime();
        String result = p.render(testCtx);
        sink = result.hashCode();
        t1 = System.nanoTime();
        long renderNs = t1 - t0;

        return new long[]{ constructNs, renderNs };
    }

    // ═══════════════════════════════════════════════
    // RESCUE: flat pipeline — 4 plain functions
    // Each function reads from ctx and writes results back to ctx.
    // No object allocation, no inheritance, no super() calls.
    // ═══════════════════════════════════════════════

    static void authenticate(Map<String, Object> ctx) {
        String token = (String) ctx.get("token");
        ctx.put("authenticated", token != null && token.startsWith("Bearer"));
        sink = ctx.size();
    }

    static void validate(Map<String, Object> ctx) {
        String userId = (String) ctx.get("user_id");
        ctx.put("valid", userId != null && !userId.isEmpty());
        sink = ctx.size();
    }

    static void loadUser(Map<String, Object> ctx) {
        ctx.put("user_name", "Alice");
        ctx.put("user_email", "alice@example.com");
        sink = ctx.size();
    }

    static void renderProfile(Map<String, Object> ctx) {
        ctx.put("rendered", true);
        sink = ctx.size();
    }

    /**
     * Rescue: flat pipeline — 4 function calls on shared context.
     * Returns [authenticate, validate, load_user, render_profile]
     */
    static long[] rescueRun() {
        long t0, t1;
        var ctx = new HashMap<>(testCtx);

        t0 = System.nanoTime();
        authenticate(ctx);
        t1 = System.nanoTime();
        long authNs = t1 - t0;

        t0 = System.nanoTime();
        validate(ctx);
        t1 = System.nanoTime();
        long validateNs = t1 - t0;

        t0 = System.nanoTime();
        loadUser(ctx);
        t1 = System.nanoTime();
        long loadNs = t1 - t0;

        t0 = System.nanoTime();
        renderProfile(ctx);
        t1 = System.nanoTime();
        long renderNs = t1 - t0;

        return new long[]{ authNs, validateNs, loadNs, renderNs };
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

        String[] crimeOps = {"construct", "render"};
        long[] crimeMedians = new long[crimeOps.length];
        for (int op = 0; op < crimeOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = crimeResults[i][op];
            crimeMedians[op] = median(col);
        }

        String[] rescueOps = {"authenticate", "validate", "load_user", "render_profile"};
        long[] rescueMedians = new long[rescueOps.length];
        for (int op = 0; op < rescueOps.length; op++) {
            long[] col = new long[RUNS];
            for (int i = 0; i < RUNS; i++) col[i] = rescueResults[i][op];
            rescueMedians[op] = median(col);
        }

        var os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        var runtime = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

        System.out.println("{");
        System.out.println("  \"chapter\": 7,");
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
