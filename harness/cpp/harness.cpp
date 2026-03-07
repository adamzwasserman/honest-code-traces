// Trace harness: captures median nanosecond costs for the six crime
// operations and four rescue operations used in the honestcode.software
// demo engine.
//
// Usage:
//   g++ -O2 -std=c++17 harness.cpp -o harness && ./harness

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <memory>
#include <numeric>
#include <string>
#include <unordered_map>
#include <stdexcept>
#include <vector>

constexpr int WARMUP = 200;
constexpr int RUNS = 1000;

using Clock = std::chrono::high_resolution_clock;

inline long long ns() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        Clock::now().time_since_epoch()).count();
}

// ═══════════════════════════════════════════════
// CRIME SCENE: mutable class with singletons
// ═══════════════════════════════════════════════

class CouponRegistry {
    static std::shared_ptr<CouponRegistry> instance;
public:
    static std::shared_ptr<CouponRegistry> getInstance() {
        if (!instance) instance = std::make_shared<CouponRegistry>();
        return instance;
    }
    static void reset() { instance.reset(); }
    double lookup(const std::string& code) const {
        return code == "SAVE10" ? 0.10 : 0;
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
    static void reset() { instance.reset(); }
    double calculate(const std::string& region, double taxable) const {
        return taxable * 0.08;
    }
};
std::shared_ptr<TaxService> TaxService::instance;

struct Item {
    std::string name;
    double price;
};

struct Order {
    std::vector<Item> items;
    double total = 0;
    double discount = 0;
    double tax = 0;
    std::string couponCode;
    long long updatedAt = 0;
};

struct CrimeResult {
    long long call, field, calc, single_, cache, time_;
};

CrimeResult crimeRun() {
    Order order;
    std::vector<Item> items = {{"Widget", 29.99}, {"Gadget", 39.99}, {"Doohickey", 19.99}};

    auto t0 = ns();
    order.items.insert(order.items.end(), items.begin(), items.end());
    auto callNs = ns() - t0;

    t0 = ns();
    order.couponCode = "SAVE10";
    order.discount = 0;
    auto fieldNs = ns() - t0;

    t0 = ns();
    order.total = std::accumulate(order.items.begin(), order.items.end(), 0.0,
        [](double sum, const Item& i) { return sum + i.price; });
    auto calcNs = ns() - t0;

    CouponRegistry::reset();
    TaxService::reset();
    t0 = ns();
    auto registry = CouponRegistry::getInstance();
    auto taxService = TaxService::getInstance();
    auto singleNs = (ns() - t0) / 2;

    t0 = ns();
    auto rate = registry->lookup(order.couponCode);
    order.discount = order.total * rate;
    auto cacheNs = ns() - t0;

    t0 = ns();
    order.updatedAt = ns();
    auto timeNs = ns() - t0;

    order.tax = taxService->calculate("NY", order.total - order.discount);

    return {callNs, fieldNs, calcNs, singleNs, cacheNs, timeNs};
}

// ═══════════════════════════════════════════════
// RESCUE: pure functions, flat data
// ═══════════════════════════════════════════════

struct OrderResult {
    double total, tax, subtotal;
};

struct FinalResult {
    double total, tax, subtotal, discount, grandTotal;
    std::string couponCode;
};

OrderResult calculateOrder(const std::vector<Item>& items, const std::string& region,
                           const std::unordered_map<std::string, double>& taxRates) {
    double total = std::accumulate(items.begin(), items.end(), 0.0,
        [](double sum, const Item& i) { return sum + i.price; });
    auto it = taxRates.find(region);
    double tax = total * (it != taxRates.end() ? it->second : 0);
    return {total, tax, total + tax};
}

FinalResult applyCoupon(const OrderResult& order, const std::string& code,
                        const std::unordered_map<std::string, double>& coupons) {
    auto it = coupons.find(code);
    double rate = it != coupons.end() ? it->second : 0;
    double discount = order.total * rate;
    return {order.total, order.tax, order.subtotal, discount, order.subtotal - discount, code};
}

struct RescueResult {
    long long call, arg, calc, ret;
};

RescueResult rescueRun() {
    std::vector<Item> items = {{"Widget", 29.99}, {"Gadget", 39.99}, {"Doohickey", 19.99}};
    std::unordered_map<std::string, double> taxRates = {{"NY", 0.08}, {"CA", 0.0725}};
    std::unordered_map<std::string, double> coupons = {{"SAVE10", 0.10}};

    auto t0 = ns();
    auto t1 = ns();
    auto callNs = t1 - t0;

    t0 = ns();
    std::string region = "NY";
    auto argNs = ns() - t0;

    t0 = ns();
    auto result = calculateOrder(items, region, taxRates);
    auto calcNs = ns() - t0;

    t0 = ns();
    auto final_ = applyCoupon(result, "SAVE10", coupons);
    auto retNs = ns() - t0;

    if (final_.grandTotal < 0) throw std::runtime_error("impossible");

    return {callNs, argNs, calcNs, retNs};
}

// ═══════════════════════════════════════════════
// HARNESS
// ═══════════════════════════════════════════════

long long median(std::vector<long long>& values) {
    std::sort(values.begin(), values.end());
    return values[values.size() / 2];
}

int main() {
    for (int i = 0; i < WARMUP; i++) { crimeRun(); rescueRun(); }

    std::vector<CrimeResult> crimeResults(RUNS);
    std::vector<RescueResult> rescueResults(RUNS);
    for (int i = 0; i < RUNS; i++) {
        crimeResults[i] = crimeRun();
        rescueResults[i] = rescueRun();
    }

    auto crimeCol = [&](auto field) {
        std::vector<long long> col(RUNS);
        for (int i = 0; i < RUNS; i++) col[i] = crimeResults[i].*field;
        return median(col);
    };

    auto rescueCol = [&](auto field) {
        std::vector<long long> col(RUNS);
        for (int i = 0; i < RUNS; i++) col[i] = rescueResults[i].*field;
        return median(col);
    };

    printf("{\n");
    printf("  \"language\": \"cpp\",\n");
#ifdef __linux__
    printf("  \"os\": \"Linux\",\n");
#elif __APPLE__
    printf("  \"os\": \"macOS\",\n");
#else
    printf("  \"os\": \"unknown\",\n");
#endif
    printf("  \"runtime\": \"g++ C++17\",\n");
    printf("  \"runs\": %d,\n", RUNS);
    printf("  \"crime\": {\n");
    printf("    \"call\": %lld,\n", crimeCol(&CrimeResult::call));
    printf("    \"field\": %lld,\n", crimeCol(&CrimeResult::field));
    printf("    \"calc\": %lld,\n", crimeCol(&CrimeResult::calc));
    printf("    \"single\": %lld,\n", crimeCol(&CrimeResult::single_));
    printf("    \"cache\": %lld,\n", crimeCol(&CrimeResult::cache));
    printf("    \"time\": %lld\n", crimeCol(&CrimeResult::time_));
    printf("  },\n");
    printf("  \"rescue\": {\n");
    printf("    \"call\": %lld,\n", rescueCol(&RescueResult::call));
    printf("    \"arg\": %lld,\n", rescueCol(&RescueResult::arg));
    printf("    \"calc\": %lld,\n", rescueCol(&RescueResult::calc));
    printf("    \"ret\": %lld\n", rescueCol(&RescueResult::ret));
    printf("  }\n");
    printf("}\n");
    return 0;
}
