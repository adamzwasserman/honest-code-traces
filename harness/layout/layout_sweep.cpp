// Experiment 1 of preregistration/memory-layout.md, in C++ at -O2.
// Sums one field across N records in five layouts and prints one JSON line
// per layout and size: nanoseconds per element, median of five repetitions.
// Build: g++ -O2 -std=c++17 layout_sweep.cpp -o layout_sweep
// Set LAYOUT_MAX_N to stop at a smaller size for a quick check.
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <numeric>
#include <random>
#include <vector>

struct Rec { int64_t a, b, c, d; };
struct Node { int64_t a, b, c, d; Node* next; };

static inline void keep(int64_t v) { asm volatile("" : : "r"(v) : "memory"); }

template <class F>
void measure(const char* layout, size_t n, size_t bytes, F traverse) {
    const size_t target = size_t(64) << 20;  // elements per repetition, at least
    const size_t loops = std::max<size_t>(1, target / n);
    for (int w = 0; w < 2; w++) keep(traverse(w));
    double per[5];
    for (int r = 0; r < 5; r++) {
        auto t0 = std::chrono::steady_clock::now();
        int64_t sum = 0;
        for (size_t l = 0; l < loops; l++) sum += traverse(int(l));
        auto t1 = std::chrono::steady_clock::now();
        keep(sum);
        double ns = std::chrono::duration<double, std::nano>(t1 - t0).count();
        per[r] = ns / (double(loops) * double(n));
    }
    std::sort(per, per + 5);
    std::printf("{\"language\":\"cpp\",\"layout\":\"%s\",\"n\":%zu,\"bytes\":%zu,"
                "\"ns_per_element_median\":%.3f,\"min\":%.3f,\"max\":%.3f}\n",
                layout, n, bytes, per[2], per[0], per[4]);
    std::fflush(stdout);
}

int main() {
    size_t max_n = 33554432;
    if (const char* e = std::getenv("LAYOUT_MAX_N")) max_n = std::strtoull(e, nullptr, 10);
    std::mt19937_64 rng(42);
    for (size_t n : {size_t(512), size_t(32768), size_t(1048576), size_t(33554432)}) {
        if (n > max_n) break;
        std::vector<size_t> order(n);
        std::iota(order.begin(), order.end(), size_t(0));
        std::shuffle(order.begin(), order.end(), rng);

        {   // L1: contiguous array of structs
            std::vector<Rec> v(n);
            for (size_t i = 0; i < n; i++) v[i] = {int64_t(i), 1, 2, 3};
            measure("L1_array_of_structs", n, n * sizeof(Rec), [&](int t) {
                v[0].a += t & 1;
                int64_t s = 0;
                for (size_t i = 0; i < n; i++) s += v[i].a;
                return s;
            });
        }
        {   // L2: struct of arrays
            std::vector<int64_t> a(n), b(n, 1), c(n, 2), d(n, 3);
            std::iota(a.begin(), a.end(), int64_t(0));
            measure("L2_struct_of_arrays", n, n * 4 * sizeof(int64_t), [&](int t) {
                a[0] += t & 1;
                int64_t s = 0;
                for (size_t i = 0; i < n; i++) s += a[i];
                return s;
            });
        }
        {   // L3, L4: heap objects allocated in order, visited in order and in random order
            std::vector<Rec*> p(n);
            for (size_t i = 0; i < n; i++) p[i] = new Rec{int64_t(i), 1, 2, 3};
            measure("L3_heap_objects_in_order", n, n * (sizeof(Rec) + 16 + sizeof(Rec*)), [&](int t) {
                p[0]->a += t & 1;
                int64_t s = 0;
                for (size_t i = 0; i < n; i++) s += p[i]->a;
                return s;
            });
            std::vector<Rec*> q(n);
            for (size_t i = 0; i < n; i++) q[i] = p[order[i]];
            measure("L4_heap_objects_random_order", n, n * (sizeof(Rec) + 16 + sizeof(Rec*)), [&](int t) {
                q[0]->a += t & 1;
                int64_t s = 0;
                for (size_t i = 0; i < n; i++) s += q[i]->a;
                return s;
            });
            for (Rec* r : p) delete r;
        }
        {   // L5: linked list whose nodes sit at random addresses
            std::vector<Node*> nodes(n);
            for (size_t i = 0; i < n; i++) nodes[i] = new Node{int64_t(i), 1, 2, 3, nullptr};
            for (size_t k = 0; k + 1 < n; k++) nodes[order[k]]->next = nodes[order[k + 1]];
            Node* head = nodes[order[0]];
            measure("L5_linked_list_random", n, n * (sizeof(Node) + 16), [&](int t) {
                head->a += t & 1;
                int64_t s = 0;
                for (Node* x = head; x; x = x->next) s += x->a;
                return s;
            });
            for (Node* x : nodes) delete x;
        }
    }
    return 0;
}
