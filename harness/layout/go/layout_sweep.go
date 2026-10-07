// Experiment 1 of preregistration/memory-layout.md, in Go.
// Sums one field across N records in five layouts and prints one JSON line
// per layout and size: nanoseconds per element, median of five repetitions.
// Run: go run layout_sweep.go
// Set LAYOUT_MAX_N to stop at a smaller size for a quick check.
package main

import (
	"fmt"
	"math/rand"
	"os"
	"runtime"
	"runtime/debug"
	"sort"
	"strconv"
	"time"
)

type Rec struct{ a, b, c, d int64 }
type Node struct {
	a, b, c, d int64
	next       *Node
}

var sink int64

func measure(layout string, n int, bytes int64, traverse func(t int) int64) {
	const target = 64 << 20
	loops := target / n
	if loops < 1 {
		loops = 1
	}
	for w := 0; w < 2; w++ {
		sink = traverse(w)
	}
	per := make([]float64, 5)
	for r := 0; r < 5; r++ {
		t0 := time.Now()
		var sum int64
		for l := 0; l < loops; l++ {
			sum += traverse(l)
		}
		d := time.Since(t0)
		sink = sum
		per[r] = float64(d.Nanoseconds()) / (float64(loops) * float64(n))
	}
	sort.Float64s(per)
	fmt.Printf("{\"language\":\"go\",\"layout\":\"%s\",\"n\":%d,\"bytes\":%d,\"ns_per_element_median\":%.3f,\"min\":%.3f,\"max\":%.3f}\n",
		layout, n, bytes, per[2], per[0], per[4])
}

func quiet(run func()) {
	runtime.GC()
	debug.SetGCPercent(-1)
	run()
	debug.SetGCPercent(100)
	runtime.GC()
}

func main() {
	maxN := 33554432
	if e := os.Getenv("LAYOUT_MAX_N"); e != "" {
		maxN, _ = strconv.Atoi(e)
	}
	rng := rand.New(rand.NewSource(42))
	for _, n := range []int{512, 32768, 1048576, 33554432} {
		if n > maxN {
			break
		}
		order := rng.Perm(n)

		func() { // L1: contiguous array of structs
			v := make([]Rec, n)
			for i := range v {
				v[i] = Rec{int64(i), 1, 2, 3}
			}
			quiet(func() {
				measure("L1_array_of_structs", n, int64(n)*32, func(t int) int64 {
					v[0].a += int64(t & 1)
					var s int64
					for i := 0; i < n; i++ {
						s += v[i].a
					}
					return s
				})
			})
		}()
		func() { // L2: struct of arrays
			a, b, c, d := make([]int64, n), make([]int64, n), make([]int64, n), make([]int64, n)
			for i := 0; i < n; i++ {
				a[i], b[i], c[i], d[i] = int64(i), 1, 2, 3
			}
			quiet(func() {
				measure("L2_struct_of_arrays", n, int64(n)*32, func(t int) int64 {
					a[0] += int64(t & 1)
					var s int64
					for i := 0; i < n; i++ {
						s += a[i]
					}
					return s
				})
			})
		}()
		func() { // L3, L4: heap objects allocated in order, visited in order and in random order
			p := make([]*Rec, n)
			for i := range p {
				p[i] = &Rec{int64(i), 1, 2, 3}
			}
			q := make([]*Rec, n)
			for i := range q {
				q[i] = p[order[i]]
			}
			quiet(func() {
				measure("L3_heap_objects_in_order", n, int64(n)*(32+8), func(t int) int64 {
					p[0].a += int64(t & 1)
					var s int64
					for i := 0; i < n; i++ {
						s += p[i].a
					}
					return s
				})
				measure("L4_heap_objects_random_order", n, int64(n)*(32+8), func(t int) int64 {
					q[0].a += int64(t & 1)
					var s int64
					for i := 0; i < n; i++ {
						s += q[i].a
					}
					return s
				})
			})
		}()
		func() { // L5: linked list whose nodes were allocated in order and linked at random
			nodes := make([]*Node, n)
			for i := range nodes {
				nodes[i] = &Node{a: int64(i), b: 1, c: 2, d: 3}
			}
			for k := 0; k+1 < n; k++ {
				nodes[order[k]].next = nodes[order[k+1]]
			}
			head := nodes[order[0]]
			nodes = nil
			quiet(func() {
				measure("L5_linked_list_random", n, int64(n)*48, func(t int) int64 {
					head.a += int64(t & 1)
					var s int64
					for x := head; x != nil; x = x.next {
						s += x.a
					}
					return s
				})
			})
		}()
	}
}
