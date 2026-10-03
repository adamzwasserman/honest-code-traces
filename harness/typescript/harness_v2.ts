// Trace harness v2 (TypeScript/Node): the Honest Code order scenario.
//
// Changes from v1: whole scenario timed once per iteration instead of six
// segments against four; primary number batched over BATCH iterations between
// two clock reads; singletons built once before measurement rather than reset
// inside the loop; every result feeds a printed sink and one input varies with
// the iteration index so nothing is eliminated; dishonest variants isolate the
// timestamp cost; percentiles instead of a lone median.
//
// The business timestamp uses Date.now(), the wall clock production code
// writes. The measurement clock (hrtime) is used only for measurement.
//
// Run: tsx harness_v2.ts

const BATCH = 1000;
const REPS = 200;
const WARMUP = 5000;
const SAMPLES = 5000;

const ns = (): bigint => process.hrtime.bigint();

let sink = 0;

// ─────────────────────────────────────────────
// Dishonest: mutable class, singletons, stamps
// ─────────────────────────────────────────────

interface Item {
  name: string;
  price: number;
}

class CouponRegistry {
  private static instance: CouponRegistry | null = null;
  static getInstance(): CouponRegistry {
    if (!CouponRegistry.instance) CouponRegistry.instance = new CouponRegistry();
    return CouponRegistry.instance;
  }
  lookup(code: string): number {
    return code === "SAVE10" ? 0.1 : 0;
  }
}

class TaxService {
  private static instance: TaxService | null = null;
  static getInstance(): TaxService {
    if (!TaxService.instance) TaxService.instance = new TaxService();
    return TaxService.instance;
  }
  calculate(region: string, taxable: number): number {
    return taxable * (region === "NY" ? 0.08 : 0.0725);
  }
}

class Order {
  private items: Item[] = [];
  private total = 0;
  private discount = 0;
  private tax = 0;
  private couponCode = "";
  private updatedAt = 0;

  constructor(private stamp: boolean) {}

  addItem(item: Item): void {
    this.items.push(item);
    this.recalculateTotal();
    this.recalculateDiscount();
    this.recalculateTax();
    if (this.stamp) this.updatedAt = Date.now();
  }

  applyCoupon(code: string): void {
    this.couponCode = code;
    this.recalculateDiscount();
    this.recalculateTax();
    if (this.stamp) this.updatedAt = Date.now();
  }

  private recalculateTotal(): void {
    let t = 0;
    for (const i of this.items) t += i.price;
    this.total = t;
  }

  private recalculateDiscount(): void {
    const registry = CouponRegistry.getInstance();
    this.discount = this.total * registry.lookup(this.couponCode);
  }

  private recalculateTax(): void {
    const svc = TaxService.getInstance();
    this.tax = svc.calculate("NY", this.total - this.discount);
  }

  grandTotal(): number {
    return this.total - this.discount + this.tax;
  }
}

function dishonestScenario(items: Item[], stamp: boolean): number {
  const order = new Order(stamp);
  for (let k = 0; k < items.length; k++) {
    order.addItem(items[k]);
  }
  order.applyCoupon("SAVE10");
  return order.grandTotal();
}

// ─────────────────────────────────────────────
// Honest: pure functions, flat data
// ─────────────────────────────────────────────

function calculateOrder(
  items: Item[],
  region: string,
  taxRates: Record<string, number>,
): { total: number; tax: number; subtotal: number } {
  let total = 0;
  for (const i of items) total += i.price;
  const tax = total * (taxRates[region] ?? 0);
  return { total, tax, subtotal: total + tax };
}

function applyCoupon(
  order: { total: number; tax: number; subtotal: number },
  code: string,
  coupons: Record<string, number>,
) {
  const discount = order.total * (coupons[code] ?? 0);
  return { ...order, discount, grandTotal: order.subtotal - discount };
}

function honestScenario(
  items: Item[],
  region: string,
  taxRates: Record<string, number>,
  coupons: Record<string, number>,
): number {
  const result = calculateOrder(items, region, taxRates);
  return applyCoupon(result, "SAVE10", coupons).grandTotal;
}

// ─────────────────────────────────────────────
// Measurement
// ─────────────────────────────────────────────

function pct(values: number[], p: number): number {
  const s = [...values].sort((a, b) => a - b);
  return s[Math.floor(p * (s.length - 1))];
}

function distribution(values: number[]) {
  return {
    p05: pct(values, 0.05), p25: pct(values, 0.25), p50: pct(values, 0.5),
    p75: pct(values, 0.75), p95: pct(values, 0.95),
  };
}

function batched(f: (i: number) => number): number {
  const out: number[] = [];
  for (let r = 0; r < REPS; r++) {
    const t0 = ns();
    for (let i = 0; i < BATCH; i++) sink += f(i);
    out.push(Number(ns() - t0) / BATCH);
  }
  return Math.round(pct(out, 0.5));
}

function perIteration(f: (i: number) => number) {
  const s: number[] = [];
  for (let i = 0; i < SAMPLES; i++) {
    const t0 = ns();
    sink += f(i);
    s.push(Number(ns() - t0));
  }
  return distribution(s);
}

function clockFloor(): number {
  const s: number[] = [];
  for (let i = 0; i < SAMPLES; i++) {
    const t0 = ns();
    const t1 = ns();
    s.push(Number(t1 - t0));
  }
  return pct(s, 0.5);
}

function main(): void {
  // Input variants are built once, before measurement, so neither side is
  // charged for constructing them.
  const inputs: Item[][] = [];
  for (let v = 0; v < 256; v++) {
    inputs.push([
      { name: "Widget", price: 29.99 + v * 0.001 },
      { name: "Gadget", price: 39.99 },
      { name: "Doohickey", price: 19.99 },
    ]);
  }
  const taxRates = { NY: 0.08, CA: 0.0725 };
  const coupons = { SAVE10: 0.1 };
  const region = "NY";

  // Build the singletons once, before measurement.
  CouponRegistry.getInstance();
  TaxService.getInstance();

  const disFull = (i: number) => dishonestScenario(inputs[i & 255], true);
  const disNoStamp = (i: number) => dishonestScenario(inputs[i & 255], false);
  const hon = (i: number) => honestScenario(inputs[i & 255], region, taxRates, coupons);

  for (let i = 0; i < WARMUP; i++) {
    sink += disFull(i);
    sink += disNoStamp(i);
    sink += hon(i);
  }

  const floor = clockFloor();
  const bFull = batched(disFull);
  const bNoStamp = batched(disNoStamp);
  const bHonest = batched(hon);
  const dFull = perIteration(disFull);
  const dHonest = perIteration(hon);

  console.log(
    JSON.stringify(
      {
        language: "typescript",
        method: `whole scenario, ${BATCH} iterations between two clock reads, median of ${REPS} batches`,
        warmup_iterations: WARMUP,
        clock_read_pair_ns: floor,
        batched_ns_per_iteration: {
          dishonest_full: bFull,
          dishonest_no_timestamp: bNoStamp,
          honest: bHonest,
        },
        ratios: {
          full_over_honest: Number((bFull / bHonest).toFixed(2)),
          no_timestamp_over_honest: Number((bNoStamp / bHonest).toFixed(2)),
        },
        per_iteration_ns: {
          note: "each sample includes one clock_read_pair_ns of instrument cost",
          dishonest_full: dFull,
          honest: dHonest,
        },
        sink: Number(sink.toFixed(3)),
      },
      null,
      2,
    ),
  );
}

main();
