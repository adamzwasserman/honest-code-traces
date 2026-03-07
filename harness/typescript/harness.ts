/**
 * Trace harness: captures median nanosecond costs for the six crime
 * operations and four rescue operations used in the honestcode.software
 * demo engine.
 *
 * Runs N iterations, discards warmup, reports median per operation.
 *
 * Usage:
 *   npx tsx harness.ts
 */

const WARMUP = 200;
const RUNS = 1000;

// ═══════════════════════════════════════════════
// CRIME SCENE: mutable Order class with singletons
// ═══════════════════════════════════════════════

class CouponRegistry {
  private static instance: CouponRegistry | null = null;
  static getInstance(): CouponRegistry {
    if (!CouponRegistry.instance) CouponRegistry.instance = new CouponRegistry();
    return CouponRegistry.instance;
  }
  lookup(code: string): number {
    if (code === "SAVE10") return 0.10;
    return 0;
  }
}

class TaxService {
  private static instance: TaxService | null = null;
  static getInstance(): TaxService {
    if (!TaxService.instance) TaxService.instance = new TaxService();
    return TaxService.instance;
  }
  calculate(region: string, taxable: number): number {
    return taxable * 0.08;
  }
}

interface Item {
  name: string;
  price: number;
}

class Order {
  items: Item[] = [];
  total = 0;
  discount = 0;
  tax = 0;
  couponCode: string | null = null;
  updatedAt: number | null = null;
}

function ns(): bigint {
  return process.hrtime.bigint();
}

function crimeRun(): bigint[] {
  const order = new Order();
  const items: Item[] = [
    { name: "Widget", price: 29.99 },
    { name: "Gadget", price: 39.99 },
    { name: "Doohickey", price: 19.99 },
  ];

  // call: method dispatch (simulate addItem call overhead)
  let t0 = ns();
  order.items.push(...items);
  let callNs = ns() - t0;

  // field: mutable field write
  t0 = ns();
  order.couponCode = "SAVE10";
  order.discount = 0;
  let fieldNs = ns() - t0;

  // calc: computation (reduce over collection)
  t0 = ns();
  order.total = order.items.reduce((sum, item) => sum + item.price, 0);
  let calcNs = ns() - t0;

  // single: singleton lookup
  t0 = ns();
  const registry = CouponRegistry.getInstance();
  const taxService = TaxService.getInstance();
  let singleNs = (ns() - t0) / 2n; // per lookup

  // cache: cache check (property lookup)
  t0 = ns();
  const rate = registry.lookup(order.couponCode);
  order.discount = order.total * rate;
  let cacheNs = ns() - t0;

  // time: timestamp capture
  t0 = ns();
  order.updatedAt = Date.now();
  let timeNs = ns() - t0;

  // calc tax to prevent dead code elimination
  const taxable = order.total - order.discount;
  order.tax = taxService.calculate("NY", taxable);

  return [callNs, fieldNs, calcNs, singleNs, cacheNs, timeNs];
}

// ═══════════════════════════════════════════════
// RESCUE: pure functions, flat data
// ═══════════════════════════════════════════════

interface OrderResult {
  total: number;
  tax: number;
  subtotal: number;
}

interface FinalResult extends OrderResult {
  couponCode: string;
  discount: number;
  grandTotal: number;
}

function calculateOrder(
  items: Item[],
  region: string,
  taxRates: Record<string, number>
): OrderResult {
  const total = items.reduce((sum, item) => sum + item.price, 0);
  const tax = total * (taxRates[region] ?? 0);
  return { total, tax, subtotal: total + tax };
}

function applyCoupon(
  order: OrderResult,
  code: string,
  coupons: Record<string, number>
): FinalResult {
  const rate = coupons[code] ?? 0;
  const discount = order.total * rate;
  return {
    ...order,
    couponCode: code,
    discount,
    grandTotal: order.subtotal - discount,
  };
}

function rescueRun(): bigint[] {
  const items: Item[] = [
    { name: "Widget", price: 29.99 },
    { name: "Gadget", price: 39.99 },
    { name: "Doohickey", price: 19.99 },
  ];
  const taxRates: Record<string, number> = { NY: 0.08, CA: 0.0725 };
  const coupons: Record<string, number> = { SAVE10: 0.10 };

  // call: function call overhead
  let t0 = ns();
  let t1 = ns();
  let callNs = t1 - t0;

  // arg: argument passing
  t0 = ns();
  const region = "NY";
  let argNs = ns() - t0;

  // calc: pure computation
  t0 = ns();
  const result = calculateOrder(items, region, taxRates);
  let calcNs = ns() - t0;

  // ret: return value construction
  t0 = ns();
  const final_ = applyCoupon(result, "SAVE10", coupons);
  let retNs = ns() - t0;

  // prevent dead code elimination
  if (final_.grandTotal < 0) throw new Error("impossible");

  return [callNs, argNs, calcNs, retNs];
}

// ═══════════════════════════════════════════════
// HARNESS: warmup, collect, median
// ═══════════════════════════════════════════════

function median(values: bigint[]): number {
  values.sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
  return Number(values[Math.floor(values.length / 2)]);
}

function main() {
  // Warmup
  for (let i = 0; i < WARMUP; i++) {
    crimeRun();
    rescueRun();
  }

  // Collect
  const crimeResults: bigint[][] = [];
  const rescueResults: bigint[][] = [];
  for (let i = 0; i < RUNS; i++) {
    crimeResults.push(crimeRun());
    rescueResults.push(rescueRun());
  }

  // Compute medians
  const crimeOps = ["call", "field", "calc", "single", "cache", "time"];
  const rescueOps = ["call", "arg", "calc", "ret"];

  const crime: Record<string, number> = {};
  for (let op = 0; op < crimeOps.length; op++) {
    const col = crimeResults.map((r) => r[op]);
    crime[crimeOps[op]] = median(col);
  }

  const rescue: Record<string, number> = {};
  for (let op = 0; op < rescueOps.length; op++) {
    const col = rescueResults.map((r) => r[op]);
    rescue[rescueOps[op]] = median(col);
  }

  const output = {
    language: "typescript",
    os: `${process.platform} ${process.arch}`,
    runtime: `Node.js ${process.version}`,
    runs: RUNS,
    crime,
    rescue,
  };

  console.log(JSON.stringify(output, null, 2));
}

main();
