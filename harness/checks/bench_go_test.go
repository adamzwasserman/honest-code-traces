package main

// Go testing benchmarks over the scenario functions in harness_v2.go.
// b.Loop keeps the loop body from being optimized away. run-checks.sh
// copies this file next to harness_v2.go and runs go test -bench.

import "testing"

var (
	benchInputs   [][]Item
	benchTax      = map[string]float64{"NY": 0.08, "CA": 0.0725}
	benchCoupons  = map[string]float64{"SAVE10": 0.10}
	benchResult   float64
)

func init() {
	benchInputs = make([][]Item, 256)
	for v := range benchInputs {
		benchInputs[v] = []Item{
			{Name: "Widget", Price: 29.99 + float64(v)*0.001},
			{Name: "Gadget", Price: 39.99},
			{Name: "Doohickey", Price: 19.99},
		}
	}
}

func BenchmarkDishonestFull(b *testing.B) {
	i := 0
	for b.Loop() {
		benchResult = dishonestScenario(benchInputs[i&255], true, false)
		i++
	}
}

func BenchmarkDishonestNoTimestamp(b *testing.B) {
	i := 0
	for b.Loop() {
		benchResult = dishonestScenario(benchInputs[i&255], false, false)
		i++
	}
}

func BenchmarkHonest(b *testing.B) {
	i := 0
	for b.Loop() {
		benchResult = honestScenario(benchInputs[i&255], "NY", benchTax, benchCoupons)
		i++
	}
}
