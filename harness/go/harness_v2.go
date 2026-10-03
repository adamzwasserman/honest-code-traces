// Trace harness v2 (Go): measures the Honest Code order scenario.
//
// Changes from v1, and why:
//
//  1. The whole scenario is timed once per iteration, not six segments on
//     the dishonest side against four on the honest side. Each timed segment
//     carries one clock read, so six against four charged the dishonest side
//     two extra clock reads before any code ran.
//  2. The primary number is batched: BATCH iterations between two clock
//     reads, so the clock costs BATCH times less than the work.
//  3. The singletons are built once, before measurement. v1 reset them
//     inside the loop, which measures construction, not lookup.
//  4. Every result feeds a sink that is printed, and the iteration indexes
//     into a table of input variants built before measurement, so nothing is
//     hoisted and neither side is charged for building its input.
//  5. Three dishonest variants isolate which structural feature costs what.
//  6. Percentiles, not a lone median.
//
// Run: go run harness_v2.go

package main

import (
	"fmt"
	"sort"
	"sync"
	"time"
)

const (
	batch   = 1000
	reps    = 200
	warmup  = 5000
	samples = 5000
)

var sink float64

// ─────────────────────────────────────────────
// Dishonest: mutable struct, singletons, stamps
// ─────────────────────────────────────────────

type Item struct {
	Name  string
	Price float64
}

type couponRegistry struct{}

func (c *couponRegistry) Lookup(code string) float64 {
	if code == "SAVE10" {
		return 0.10
	}
	return 0
}

type taxService struct{}

func (t *taxService) Calculate(region string, taxable float64) float64 {
	if region == "NY" {
		return taxable * 0.08
	}
	return taxable * 0.0725
}

var (
	couponOnce     sync.Once
	couponInstance *couponRegistry
	taxOnce        sync.Once
	taxInstance    *taxService
)

func getCouponRegistry() *couponRegistry {
	couponOnce.Do(func() { couponInstance = &couponRegistry{} })
	return couponInstance
}

func getTaxService() *taxService {
	taxOnce.Do(func() { taxInstance = &taxService{} })
	return taxInstance
}

type Order struct {
	Items      []Item
	Total      float64
	Discount   float64
	Tax        float64
	CouponCode string
	UpdatedAt  int64
	stamp      bool
}

func (o *Order) AddItem(item Item) {
	o.Items = append(o.Items, item)
	o.recalculateTotal()
	o.recalculateDiscount()
	o.recalculateTax()
	if o.stamp {
		o.UpdatedAt = time.Now().UnixNano()
	}
}

func (o *Order) ApplyCoupon(code string) {
	o.CouponCode = code
	o.recalculateDiscount()
	o.recalculateTax()
	if o.stamp {
		o.UpdatedAt = time.Now().UnixNano()
	}
}

func (o *Order) recalculateTotal() {
	sum := 0.0
	for _, i := range o.Items {
		sum += i.Price
	}
	o.Total = sum
}

func (o *Order) recalculateDiscount() {
	registry := getCouponRegistry()
	o.Discount = o.Total * registry.Lookup(o.CouponCode)
}

func (o *Order) recalculateTax() {
	svc := getTaxService()
	o.Tax = svc.Calculate("NY", o.Total-o.Discount)
}

func (o *Order) GrandTotal() float64 { return o.Total - o.Discount + o.Tax }

func dishonestScenario(items []Item, stamp, prealloc bool) float64 {
	order := &Order{stamp: stamp}
	if prealloc {
		order.Items = make([]Item, 0, 4)
	}
	for _, it := range items {
		order.AddItem(it)
	}
	order.ApplyCoupon("SAVE10")
	return order.GrandTotal()
}

// ─────────────────────────────────────────────
// Honest: pure functions, flat data
// ─────────────────────────────────────────────

type OrderResult struct {
	Total, Tax, Subtotal float64
}

type FinalResult struct {
	Total, Tax, Subtotal, Discount, GrandTotal float64
}

func CalculateOrder(items []Item, region string, taxRates map[string]float64) OrderResult {
	total := 0.0
	for _, i := range items {
		total += i.Price
	}
	tax := total * taxRates[region]
	return OrderResult{Total: total, Tax: tax, Subtotal: total + tax}
}

func ApplyCoupon(o OrderResult, code string, coupons map[string]float64) FinalResult {
	discount := o.Total * coupons[code]
	return FinalResult{
		Total: o.Total, Tax: o.Tax, Subtotal: o.Subtotal,
		Discount: discount, GrandTotal: o.Subtotal - discount,
	}
}

func honestScenario(items []Item, region string,
	taxRates, coupons map[string]float64) float64 {
	result := CalculateOrder(items, region, taxRates)
	final := ApplyCoupon(result, "SAVE10", coupons)
	return final.GrandTotal
}

// ─────────────────────────────────────────────
// Measurement
// ─────────────────────────────────────────────

func pct(v []int64, p float64) int64 {
	s := make([]int64, len(v))
	copy(s, v)
	sort.Slice(s, func(a, b int) bool { return s[a] < s[b] })
	return s[int(p*float64(len(s)-1))]
}

type dist struct{ p05, p25, p50, p75, p95 int64 }

func distribution(v []int64) dist {
	return dist{pct(v, 0.05), pct(v, 0.25), pct(v, 0.50), pct(v, 0.75), pct(v, 0.95)}
}

func batched(f func(int) float64) int64 {
	out := make([]int64, 0, reps)
	for r := 0; r < reps; r++ {
		t0 := time.Now()
		for i := 0; i < batch; i++ {
			sink += f(i)
		}
		out = append(out, time.Since(t0).Nanoseconds()/batch)
	}
	return pct(out, 0.50)
}

func perIteration(f func(int) float64) dist {
	s := make([]int64, 0, samples)
	for i := 0; i < samples; i++ {
		t0 := time.Now()
		sink += f(i)
		s = append(s, time.Since(t0).Nanoseconds())
	}
	return distribution(s)
}

func clockFloor() int64 {
	s := make([]int64, 0, samples)
	for i := 0; i < samples; i++ {
		t0 := time.Now()
		s = append(s, time.Since(t0).Nanoseconds())
	}
	return pct(s, 0.50)
}

func main() {
	// Input variants are built once, before measurement, so neither side is
	// charged for constructing them.
	const variants = 256
	inputs := make([][]Item, variants)
	for v := 0; v < variants; v++ {
		inputs[v] = []Item{
			{"Widget", 29.99 + float64(v)*0.001},
			{"Gadget", 39.99},
			{"Doohickey", 19.99},
		}
	}
	taxRates := map[string]float64{"NY": 0.08, "CA": 0.0725}
	coupons := map[string]float64{"SAVE10": 0.10}
	region := "NY"

	// Build the singletons once, before measurement.
	getCouponRegistry()
	getTaxService()

	disFull := func(i int) float64 { return dishonestScenario(inputs[i&255], true, false) }
	disNoStamp := func(i int) float64 { return dishonestScenario(inputs[i&255], false, false) }
	disPrealloc := func(i int) float64 { return dishonestScenario(inputs[i&255], false, true) }
	hon := func(i int) float64 { return honestScenario(inputs[i&255], region, taxRates, coupons) }

	for i := 0; i < warmup; i++ {
		sink += disFull(i)
		sink += disNoStamp(i)
		sink += disPrealloc(i)
		sink += hon(i)
	}

	floor := clockFloor()
	bFull := batched(disFull)
	bNoStamp := batched(disNoStamp)
	bPrealloc := batched(disPrealloc)
	bHonest := batched(hon)
	dFull := perIteration(disFull)
	dHonest := perIteration(hon)

	fmt.Printf("{\n")
	fmt.Printf("  \"language\": \"go\",\n")
	fmt.Printf("  \"method\": \"whole scenario, %d iterations between two clock reads, median of %d batches\",\n", batch, reps)
	fmt.Printf("  \"warmup_iterations\": %d,\n", warmup)
	fmt.Printf("  \"clock_read_pair_ns\": %d,\n", floor)
	fmt.Printf("  \"batched_ns_per_iteration\": {\n")
	fmt.Printf("    \"dishonest_full\": %d,\n", bFull)
	fmt.Printf("    \"dishonest_no_timestamp\": %d,\n", bNoStamp)
	fmt.Printf("    \"dishonest_no_timestamp_prealloc\": %d,\n", bPrealloc)
	fmt.Printf("    \"honest\": %d\n", bHonest)
	fmt.Printf("  },\n")
	fmt.Printf("  \"ratios\": {\n")
	fmt.Printf("    \"full_over_honest\": %.2f,\n", float64(bFull)/float64(bHonest))
	fmt.Printf("    \"no_timestamp_over_honest\": %.2f,\n", float64(bNoStamp)/float64(bHonest))
	fmt.Printf("    \"no_timestamp_prealloc_over_honest\": %.2f\n", float64(bPrealloc)/float64(bHonest))
	fmt.Printf("  },\n")
	fmt.Printf("  \"per_iteration_ns\": {\n")
	fmt.Printf("    \"note\": \"each sample includes one clock_read_pair_ns of instrument cost\",\n")
	fmt.Printf("    \"dishonest_full\": {\"p05\": %d, \"p25\": %d, \"p50\": %d, \"p75\": %d, \"p95\": %d},\n",
		dFull.p05, dFull.p25, dFull.p50, dFull.p75, dFull.p95)
	fmt.Printf("    \"honest\":         {\"p05\": %d, \"p25\": %d, \"p50\": %d, \"p75\": %d, \"p95\": %d}\n",
		dHonest.p05, dHonest.p25, dHonest.p50, dHonest.p75, dHonest.p95)
	fmt.Printf("  },\n")
	fmt.Printf("  \"sink\": %.3f\n", sink)
	fmt.Printf("}\n")
}
