// Google Benchmark over the scenario functions in ../cpp/harness_v2.cpp.
// The harness main() is renamed away so its scenarios compile into this file.
// DoNotOptimize keeps each result alive. Run by run-checks.sh.
#define main harness_v2_main
#include "../cpp/harness_v2.cpp"
#undef main

#include <benchmark/benchmark.h>

static std::vector<std::vector<Item>> makeInputs() {
    std::vector<std::vector<Item>> inputs(256);
    for (int v = 0; v < 256; v++) {
        inputs[v] = {{"Widget", 29.99 + v * 0.001},
                     {"Gadget", 39.99},
                     {"Doohickey", 19.99}};
    }
    return inputs;
}

static const std::vector<std::vector<Item>> kInputs = makeInputs();
static const std::unordered_map<std::string, double> kTaxRates = {{"NY", 0.08}, {"CA", 0.0725}};
static const std::unordered_map<std::string, double> kCoupons = {{"SAVE10", 0.10}};
static const std::string kRegion = "NY";

static void DishonestFull(benchmark::State& state) {
    int i = 0;
    for (auto _ : state) {
        double r = dishonestScenario(kInputs[i++ & 255], true, false);
        benchmark::DoNotOptimize(r);
    }
}

static void DishonestNoTimestamp(benchmark::State& state) {
    int i = 0;
    for (auto _ : state) {
        double r = dishonestScenario(kInputs[i++ & 255], false, false);
        benchmark::DoNotOptimize(r);
    }
}

static void Honest(benchmark::State& state) {
    int i = 0;
    for (auto _ : state) {
        double r = honestScenario(kInputs[i++ & 255], kRegion, kTaxRates, kCoupons);
        benchmark::DoNotOptimize(r);
    }
}

BENCHMARK(DishonestFull);
BENCHMARK(DishonestNoTimestamp);
BENCHMARK(Honest);
BENCHMARK_MAIN();
