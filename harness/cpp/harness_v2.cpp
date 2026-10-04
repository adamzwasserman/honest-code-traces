// Trace harness v2: measures the Honest Code order scenario in C++.
//
// Changes from v1, and why:
//
//   1. The whole scenario is timed once per iteration, not six segments on
//      the dishonest side against four on the honest side. Each timed
//      segment carries one clock read of overhead, so counting six against
//      four charged the dishonest side roughly two extra clock reads before
//      any code ran.
//
//   2. The primary number is a batched measurement: B iterations between
//      two clock reads, so the clock cost per iteration is B times smaller
//      than the work. The per-iteration distribution is reported too, as a
//      secondary figure, next to the measured cost of the clock itself.
//
//   3. The singletons are constructed once, before measurement. v1 called
//      reset() inside the loop, so every iteration paid two make_shared
//      allocations. That measures singleton construction, not lookup.
//
//   4. Every result feeds a sink that is printed, and the iteration indexes
//      into a table of input variants built before measurement, so -O2 cannot
//      hoist or delete the work and neither side is charged for building its
//      input.
//
//   5. Three variants are reported, so a reader can see which structural
//      feature carries the cost: the full scenario, the scenario without
//      the two timestamp writes, and the scenario with the order's item
//      vector reserved.
//
//   6. Percentiles are reported, not a lone median.
//
// Build: g++ -O2 -std=c++17 harness_v2.cpp -o harness_v2

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <memory>
#include <numeric>
#include <string>
#include <unordered_map>
#include <vector>

constexpr int BATCH = 1000;   // iterations between two clock reads
constexpr int REPS = 200;     // batched measurements per variant
constexpr int WARMUP = 5000;  // iterations before any measurement
constexpr int SAMPLES = 5000; // per-iteration samples for the distribution

using Clock = std::chrono::steady_clock;

static inline long long ns() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               Clock::now().time_since_epoch())
        .count();
}

// Sink: printed at the end so nothing measured can be optimized away.
static volatile double g_sink = 0;

// Optimization barriers. At -O2 the compiler will otherwise hoist the
// loop-invariant hash lookups and singleton loads out of the batch loop
// entirely, which measures an empty loop. Both scenarios are marked noinline
// so each pays exactly one call per iteration, and the barrier forces the
// input and the result to be treated as opaque.
#define NOINLINE __attribute__((noinline))

template <typename T>
static inline void doNotOptimize(T const& value) {
    asm volatile("" : : "r,m"(value) : "memory");
}

// ─────────────────────────────────────────────────────────────
// Dishonest: mutable class, separate calculation methods, singletons, stamps
// ─────────────────────────────────────────────────────────────

struct Item {
    std::string name;
    double price;
};

class CouponRegistry {
    static std::shared_ptr<CouponRegistry> instance;

  public:
    static std::shared_ptr<CouponRegistry> getInstance() {
        if (!instance) instance = std::make_shared<CouponRegistry>();
        return instance;
    }
    double lookup(const std::string& code) const {
        return code == "SAVE10" ? 0.10 : 0.0;
    }
};
std::shared_ptr<CouponRegistry> CouponRegistry::instance;

class TaxService {
    static std::shared_ptr<TaxService> instance;

  public:
    static std::shared_ptr<TaxService> getInstance() {
        if (!instance) instance = std::make_shared<TaxService>();
        return instance;
    }
    double calculate(const std::string& region, double taxable) const {
        return region == "NY" ? taxable * 0.08 : taxable * 0.0725;
    }
};
std::shared_ptr<TaxService> TaxService::instance;

// Order keeps its state in mutable fields and exposes separate, explicit
// calculation methods. Mutating methods only record input. The caller must
// run calcTotal, calcDiscount and calcTax in that order, because each reads
// the field the one before it wrote. Order matters: calcDiscount before
// calcTotal uses a stale total.
class Order {
    std::vector<Item> items_;
    double total_ = 0;
    double discount_ = 0;
    double tax_ = 0;
    std::string couponCode_;
    long long updatedAt_ = 0;
    bool stamp_;

  public:
    explicit Order(bool stamp, bool reserve) : stamp_(stamp) {
        if (reserve) items_.reserve(4);
    }

    void addItem(const Item& item) {
        items_.push_back(item);
        if (stamp_) updatedAt_ = ns();
    }

    void applyCoupon(const std::string& code) {
        couponCode_ = code;
        if (stamp_) updatedAt_ = ns();
    }

    void calcTotal() {
        total_ = std::accumulate(
            items_.begin(), items_.end(), 0.0,
            [](double sum, const Item& i) { return sum + i.price; });
    }

    void calcDiscount() {
        auto registry = CouponRegistry::getInstance();
        discount_ = total_ * registry->lookup(couponCode_);
    }

    void calcTax() {
        auto taxService = TaxService::getInstance();
        tax_ = taxService->calculate("NY", total_ - discount_);
    }

    double grandTotal() const { return total_ - discount_ + tax_; }
};

// The caller adds the items, applies the coupon, then runs the calculations
// in dependency order: total, discount, tax.
NOINLINE static double dishonestScenario(const std::vector<Item>& items,
                                         bool stamp, bool reserve) {
    doNotOptimize(items);
    Order order(stamp, reserve);
    for (size_t k = 0; k < items.size(); k++) order.addItem(items[k]);
    order.applyCoupon("SAVE10");
    order.calcTotal();
    order.calcDiscount();
    order.calcTax();
    double g = order.grandTotal();
    doNotOptimize(g);
    return g;
}

// ─────────────────────────────────────────────────────────────
// Honest: pure functions, flat data, immutable returns
// ─────────────────────────────────────────────────────────────

struct OrderResult {
    double total, discount, subtotal;
};

struct FinalResult {
    double total, discount, subtotal, tax, grandTotal;
};

static OrderResult applyCoupon(
    const std::vector<Item>& items, const std::string& code,
    const std::unordered_map<std::string, double>& coupons) {
    double total = std::accumulate(
        items.begin(), items.end(), 0.0,
        [](double sum, const Item& i) { return sum + i.price; });
    auto it = coupons.find(code);
    double discount = total * (it != coupons.end() ? it->second : 0.0);
    return {total, discount, total - discount};
}

static FinalResult calculateTax(
    const OrderResult& order, const std::string& region,
    const std::unordered_map<std::string, double>& taxRates) {
    auto it = taxRates.find(region);
    double tax = order.subtotal * (it != taxRates.end() ? it->second : 0.0);
    return {order.total, order.discount, order.subtotal, tax,
            order.subtotal + tax};
}

NOINLINE static double honestScenario(
    const std::vector<Item>& items, const std::string& region,
    const std::unordered_map<std::string, double>& taxRates,
    const std::unordered_map<std::string, double>& coupons) {
    doNotOptimize(items);
    doNotOptimize(taxRates);
    doNotOptimize(coupons);
    auto result = applyCoupon(items, "SAVE10", coupons);
    auto final_ = calculateTax(result, region, taxRates);
    double g = final_.grandTotal;
    doNotOptimize(g);
    return g;
}

// ─────────────────────────────────────────────────────────────
// Measurement
// ─────────────────────────────────────────────────────────────

static long long pct(std::vector<long long> v, double p) {
    std::sort(v.begin(), v.end());
    size_t idx = static_cast<size_t>(p * (v.size() - 1));
    return v[idx];
}

struct Dist {
    long long p05, p25, p50, p75, p95;
};

static Dist distribution(std::vector<long long> v) {
    return {pct(v, 0.05), pct(v, 0.25), pct(v, 0.50), pct(v, 0.75),
            pct(v, 0.95)};
}

template <typename F>
static long long batched(F f) {
    // Median per-iteration cost across REPS batches of BATCH iterations.
    std::vector<long long> reps;
    reps.reserve(REPS);
    for (int r = 0; r < REPS; r++) {
        long long t0 = ns();
        for (int i = 0; i < BATCH; i++) g_sink += f(i);
        long long dt = ns() - t0;
        reps.push_back(dt / BATCH);
    }
    return pct(reps, 0.50);
}

template <typename F>
static Dist perIteration(F f) {
    std::vector<long long> s;
    s.reserve(SAMPLES);
    for (int i = 0; i < SAMPLES; i++) {
        long long t0 = ns();
        g_sink += f(i);
        s.push_back(ns() - t0);
    }
    return distribution(s);
}

static long long clockFloor() {
    // Cost of the two clock reads that bracket any single-segment timing.
    std::vector<long long> s;
    s.reserve(SAMPLES);
    for (int i = 0; i < SAMPLES; i++) {
        long long t0 = ns();
        long long t1 = ns();
        s.push_back(t1 - t0);
    }
    return pct(s, 0.50);
}

int main() {
    // Input variants are built once, before measurement, so neither side is
    // charged for constructing them. Indexing by iteration stops the compiler
    // treating the input as a constant.
    constexpr int VARIANTS = 256;
    std::vector<std::vector<Item>> inputs(VARIANTS);
    for (int v = 0; v < VARIANTS; v++) {
        inputs[v] = {{"Widget", 29.99 + v * 0.001},
                     {"Gadget", 39.99},
                     {"Doohickey", 19.99}};
    }
    const std::unordered_map<std::string, double> taxRates = {{"NY", 0.08},
                                                              {"CA", 0.0725}};
    const std::unordered_map<std::string, double> coupons = {{"SAVE10", 0.10}};
    const std::string region = "NY";

    // Construct the singletons once, before measurement.
    CouponRegistry::getInstance();
    TaxService::getInstance();

    auto dis_full = [&](int i) { return dishonestScenario(inputs[i & 255], true, false); };
    auto dis_nostamp = [&](int i) { return dishonestScenario(inputs[i & 255], false, false); };
    auto dis_reserved = [&](int i) { return dishonestScenario(inputs[i & 255], false, true); };
    auto hon = [&](int i) { return honestScenario(inputs[i & 255], region, taxRates, coupons); };

    for (int i = 0; i < WARMUP; i++) {
        g_sink += dis_full(i);
        g_sink += dis_nostamp(i);
        g_sink += dis_reserved(i);
        g_sink += hon(i);
    }

    long long floor_ns = clockFloor();

    long long b_full = batched(dis_full);
    long long b_nostamp = batched(dis_nostamp);
    long long b_reserved = batched(dis_reserved);
    long long b_honest = batched(hon);

    Dist d_full = perIteration(dis_full);
    Dist d_honest = perIteration(hon);

    printf("{\n");
    printf("  \"language\": \"cpp\",\n");
    printf("  \"runtime\": \"g++ -O2 -std=c++17\",\n");
    printf("  \"method\": \"whole scenario, %d iterations between two clock reads, median of %d batches\",\n",
           BATCH, REPS);
    printf("  \"warmup_iterations\": %d,\n", WARMUP);
    printf("  \"clock_read_pair_ns\": %lld,\n", floor_ns);
    printf("  \"batched_ns_per_iteration\": {\n");
    printf("    \"dishonest_full\": %lld,\n", b_full);
    printf("    \"dishonest_no_timestamp\": %lld,\n", b_nostamp);
    printf("    \"dishonest_no_timestamp_reserved\": %lld,\n", b_reserved);
    printf("    \"honest\": %lld\n", b_honest);
    printf("  },\n");
    printf("  \"ratios\": {\n");
    printf("    \"full_over_honest\": %.2f,\n", (double)b_full / b_honest);
    printf("    \"no_timestamp_over_honest\": %.2f,\n", (double)b_nostamp / b_honest);
    printf("    \"no_timestamp_reserved_over_honest\": %.2f\n", (double)b_reserved / b_honest);
    printf("  },\n");
    printf("  \"per_iteration_ns\": {\n");
    printf("    \"note\": \"each sample includes one clock_read_pair_ns of instrument cost\",\n");
    printf("    \"dishonest_full\": {\"p05\": %lld, \"p25\": %lld, \"p50\": %lld, \"p75\": %lld, \"p95\": %lld},\n",
           d_full.p05, d_full.p25, d_full.p50, d_full.p75, d_full.p95);
    printf("    \"honest\":         {\"p05\": %lld, \"p25\": %lld, \"p50\": %lld, \"p75\": %lld, \"p95\": %lld}\n",
           d_honest.p05, d_honest.p25, d_honest.p50, d_honest.p75, d_honest.p95);
    printf("  },\n");
    printf("  \"sink\": %.3f\n", (double)g_sink);
    printf("}\n");
    return 0;
}
