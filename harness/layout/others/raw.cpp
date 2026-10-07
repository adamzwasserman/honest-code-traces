// Raw element access in C++ at -O2: each element is read and handed to an
// empty asm statement, so the compiler cannot drop the read. L1 is a
// contiguous array, L4 is heap objects visited in random order. The empty
// loop passes only the index.
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <numeric>
#include <random>
#include <vector>

struct Rec { int64_t a, b, c, d; };
static inline void keep(int64_t v) { asm volatile("" : : "r"(v)); }

template <class F>
double timed(size_t n, F f, int reps, double* lo, double* hi) {
    f();
    std::vector<double> per;
    for (int r = 0; r < reps; r++) {
        auto t0 = std::chrono::steady_clock::now();
        f();
        per.push_back(std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - t0).count() / double(n));
    }
    std::sort(per.begin(), per.end());
    *lo = per.front(); *hi = per.back();
    return per[per.size() / 2];
}

int main() {
    std::mt19937_64 rng(42);
    for (size_t n : {size_t(1048576), size_t(33554432)}) {
        std::vector<size_t> order(n);
        std::iota(order.begin(), order.end(), size_t(0));
        std::shuffle(order.begin(), order.end(), rng);
        double lo, hi;
        double empty = timed(n, [&] { for (size_t i = 0; i < n; i++) keep(int64_t(i)); }, 5, &lo, &hi);
        {
            std::vector<int64_t> v(n);
            std::iota(v.begin(), v.end(), int64_t(0));
            double m = timed(n, [&] { for (size_t i = 0; i < n; i++) keep(v[i]); }, 5, &lo, &hi);
            std::printf("{\"language\":\"cpp\",\"idiom\":\"contiguous array\",\"n\":%zu,\"ns_per_element_median\":%.3f,\"empty_loop\":%.3f,\"net\":%.3f,\"min\":%.3f,\"max\":%.3f}\n", n, m, empty, m - empty, lo, hi);
        }
        {
            std::vector<Rec*> p(n);
            for (size_t i = 0; i < n; i++) p[i] = new Rec{int64_t(i), 1, 2, 3};
            std::vector<Rec*> q(n);
            for (size_t i = 0; i < n; i++) q[i] = p[order[i]];
            double m = timed(n, [&] { for (size_t i = 0; i < n; i++) keep(q[i]->a); }, 5, &lo, &hi);
            std::printf("{\"language\":\"cpp\",\"idiom\":\"scattered heap objects\",\"n\":%zu,\"ns_per_element_median\":%.3f,\"empty_loop\":%.3f,\"net\":%.3f,\"min\":%.3f,\"max\":%.3f}\n", n, m, empty, m - empty, lo, hi);
            for (Rec* r : p) delete r;
        }
    }
}
