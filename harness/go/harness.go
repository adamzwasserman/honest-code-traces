// Trace harness: captures median nanosecond costs for the six crime
// operations and four rescue operations used in the honestcode.software
// demo engine.
//
// Runs N iterations, discards warmup, reports median per operation.
// Uses batched operations (100 iterations per measurement) to overcome
// macOS timer granularity (~42ns).
//
// Usage:
//
//	go run harness.go
package main

import (
	"encoding/json"
	"fmt"
	"os"
	"runtime"
	"sort"
	"sync"
	"time"
)

const (
	warmup = 200
	runs   = 1000
	batch  = 100 // operations per measurement to overcome timer granularity
)

// ═══════════════════════════════════════════════
// CRIME SCENE: mutable struct with singleton pattern
// ═══════════════════════════════════════════════

type CouponRegistry struct{}

var (
	couponOnce     sync.Once
	couponInstance *CouponRegistry
)

func GetCouponRegistry() *CouponRegistry {
	couponOnce.Do(func() {
		couponInstance = &CouponRegistry{}
	})
	return couponInstance
}

func (r *CouponRegistry) Lookup(code string) float64 {
	if code == "SAVE10" {
		return 0.10
	}
	return 0
}

type TaxService struct{}

var (
	taxOnce     sync.Once
	taxInstance *TaxService
)

func GetTaxService() *TaxService {
	taxOnce.Do(func() {
		taxInstance = &TaxService{}
	})
	return taxInstance
}

func (s *TaxService) Calculate(region string, taxable float64) float64 {
	return taxable * 0.08
}

type Item struct {
	Name  string
	Price float64
}

type Order struct {
	Items      []Item
	Total      float64
	Discount   float64
	Tax        float64
	CouponCode string
	UpdatedAt  time.Time
}

// crimeRun measures each operation over a batch and returns per-op nanoseconds.
func crimeRun() [6]int64 {
	items := []Item{
		{"Widget", 29.99},
		{"Gadget", 39.99},
		{"Doohickey", 19.99},
	}

	// call: method dispatch (append items)
	start := time.Now()
	for b := 0; b < batch; b++ {
		order := &Order{}
		order.Items = append(order.Items, items...)
		_ = order
	}
	callNs := time.Since(start).Nanoseconds() / batch

	// field: mutable field write
	start = time.Now()
	for b := 0; b < batch; b++ {
		order := &Order{}
		order.CouponCode = "SAVE10"
		order.Discount = 0
		_ = order
	}
	fieldNs := time.Since(start).Nanoseconds() / batch

	// calc: computation (loop over collection)
	order := &Order{Items: items}
	start = time.Now()
	for b := 0; b < batch; b++ {
		total := 0.0
		for _, item := range order.Items {
			total += item.Price
		}
		order.Total = total
	}
	calcNs := time.Since(start).Nanoseconds() / batch

	// single: singleton lookup (sync.Once)
	start = time.Now()
	for b := 0; b < batch; b++ {
		couponOnce = sync.Once{}
		couponInstance = nil
		taxOnce = sync.Once{}
		taxInstance = nil
		_ = GetCouponRegistry()
		_ = GetTaxService()
	}
	singleNs := time.Since(start).Nanoseconds() / batch / 2 // per lookup

	// cache: cache check (method call with string comparison)
	registry := GetCouponRegistry()
	start = time.Now()
	for b := 0; b < batch; b++ {
		rate := registry.Lookup("SAVE10")
		order.Discount = order.Total * rate
	}
	cacheNs := time.Since(start).Nanoseconds() / batch

	// time: timestamp capture
	start = time.Now()
	for b := 0; b < batch; b++ {
		order.UpdatedAt = time.Now()
	}
	timeNs := time.Since(start).Nanoseconds() / batch

	// prevent dead code elimination
	taxService := GetTaxService()
	taxable := order.Total - order.Discount
	order.Tax = taxService.Calculate("NY", taxable)
	_ = order.Tax

	return [6]int64{callNs, fieldNs, calcNs, singleNs, cacheNs, timeNs}
}

// ═══════════════════════════════════════════════
// RESCUE: pure functions, flat data
// ═══════════════════════════════════════════════

type OrderResult struct {
	Total    float64
	Tax      float64
	Subtotal float64
}

type FinalResult struct {
	Total      float64
	Tax        float64
	Subtotal   float64
	CouponCode string
	Discount   float64
	GrandTotal float64
}

func calculateOrder(items []Item, region string, taxRates map[string]float64) OrderResult {
	total := 0.0
	for _, item := range items {
		total += item.Price
	}
	rate, ok := taxRates[region]
	if !ok {
		rate = 0
	}
	tax := total * rate
	return OrderResult{Total: total, Tax: tax, Subtotal: total + tax}
}

func applyCoupon(order OrderResult, code string, coupons map[string]float64) FinalResult {
	rate, ok := coupons[code]
	if !ok {
		rate = 0
	}
	discount := order.Total * rate
	return FinalResult{
		Total:      order.Total,
		Tax:        order.Tax,
		Subtotal:   order.Subtotal,
		CouponCode: code,
		Discount:   discount,
		GrandTotal: order.Subtotal - discount,
	}
}

func rescueRun() [4]int64 {
	items := []Item{
		{"Widget", 29.99},
		{"Gadget", 39.99},
		{"Doohickey", 19.99},
	}
	taxRates := map[string]float64{"NY": 0.08, "CA": 0.0725}
	coupons := map[string]float64{"SAVE10": 0.10}

	// call: function call overhead (empty call pair)
	start := time.Now()
	for b := 0; b < batch; b++ {
		_ = time.Now()
	}
	callNs := time.Since(start).Nanoseconds() / batch

	// arg: argument passing
	start = time.Now()
	for b := 0; b < batch; b++ {
		region := "NY"
		_ = region
	}
	argNs := time.Since(start).Nanoseconds() / batch

	// calc: pure computation
	start = time.Now()
	var result OrderResult
	for b := 0; b < batch; b++ {
		result = calculateOrder(items, "NY", taxRates)
	}
	calcNs := time.Since(start).Nanoseconds() / batch

	// ret: return value construction
	start = time.Now()
	var final FinalResult
	for b := 0; b < batch; b++ {
		final = applyCoupon(result, "SAVE10", coupons)
	}
	retNs := time.Since(start).Nanoseconds() / batch

	// prevent dead code elimination
	if final.GrandTotal < 0 {
		panic("impossible")
	}

	return [4]int64{callNs, argNs, calcNs, retNs}
}

// ═══════════════════════════════════════════════
// HARNESS: warmup, collect, median
// ═══════════════════════════════════════════════

func medianInt64(values []int64) int64 {
	sort.Slice(values, func(i, j int) bool { return values[i] < values[j] })
	return values[len(values)/2]
}

type output struct {
	Language string           `json:"language"`
	OS       string           `json:"os"`
	Runtime  string           `json:"runtime"`
	Runs     int              `json:"runs"`
	Crime    map[string]int64 `json:"crime"`
	Rescue   map[string]int64 `json:"rescue"`
}

func main() {
	// Warmup
	for i := 0; i < warmup; i++ {
		crimeRun()
		rescueRun()
	}

	// Collect
	crimeResults := make([][6]int64, runs)
	rescueResults := make([][4]int64, runs)
	for i := 0; i < runs; i++ {
		crimeResults[i] = crimeRun()
		rescueResults[i] = rescueRun()
	}

	// Compute medians
	crimeOps := []string{"call", "field", "calc", "single", "cache", "time"}
	rescueOps := []string{"call", "arg", "calc", "ret"}

	crime := make(map[string]int64)
	for op := 0; op < len(crimeOps); op++ {
		col := make([]int64, runs)
		for i := 0; i < runs; i++ {
			col[i] = crimeResults[i][op]
		}
		crime[crimeOps[op]] = medianInt64(col)
	}

	rescue := make(map[string]int64)
	for op := 0; op < len(rescueOps); op++ {
		col := make([]int64, runs)
		for i := 0; i < runs; i++ {
			col[i] = rescueResults[i][op]
		}
		rescue[rescueOps[op]] = medianInt64(col)
	}

	out := output{
		Language: "go",
		OS:       fmt.Sprintf("%s %s", runtime.GOOS, runtime.GOARCH),
		Runtime:  runtime.Version(),
		Runs:     runs,
		Crime:    crime,
		Rescue:   rescue,
	}

	enc := json.NewEncoder(os.Stdout)
	enc.SetIndent("", "  ")
	enc.Encode(out)
}
