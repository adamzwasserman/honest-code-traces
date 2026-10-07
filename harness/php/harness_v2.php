<?php
// Trace harness v2 (PHP): the Honest Code order scenario.
//
// Changes from v1:
//   1. The whole scenario is timed once per iteration, not six segments on the
//      dishonest side against four on the honest side. Each segment carries one
//      clock read, so six against four charged the dishonest side two extra
//      clock reads before any code ran.
//   2. The primary number is batched: BATCH iterations between two clock reads.
//   3. The singletons are built once, before measurement. v1 reset them inside
//      the loop, which measures construction, not lookup.
//   4. Every result feeds a sink that is printed, and the iteration indexes
//      into a table of input variants built before measurement, so neither side
//      is charged for building its input.
//   5. A dishonest variant without the timestamp writes isolates that cost.
//   6. Percentiles, not a lone median.
//
// The business timestamp uses microtime(), the wall clock production code
// writes. hrtime() is used only for measurement.
//
// Run: php harness_v2.php

const BATCH = 1000;
const REPS = 200;
const WARMUP = 5000;
const SAMPLES = 5000;

function ns(): int { return hrtime(true); }

$GLOBALS['sink'] = 0.0;

// ─────────────────────────────────────────────
// Dishonest: mutable class, singletons, stamps
// ─────────────────────────────────────────────

final class CouponRegistry {
    private static ?CouponRegistry $instance = null;
    public static function getInstance(): CouponRegistry {
        if (self::$instance === null) self::$instance = new CouponRegistry();
        return self::$instance;
    }
    public function lookup(string $code): float {
        return $code === 'SAVE10' ? 0.10 : 0.0;
    }
}

final class TaxService {
    private static ?TaxService $instance = null;
    public static function getInstance(): TaxService {
        if (self::$instance === null) self::$instance = new TaxService();
        return self::$instance;
    }
    public function calculate(string $region, float $taxable): float {
        return $taxable * ($region === 'NY' ? 0.08 : 0.0725);
    }
}

// Order has separate, explicit calculation methods. addItem and applyCoupon
// only record data. The caller must run calcTotal, calcDiscount and calcTax
// in that order, because each reads the result of the one before it.
final class Order {
    private array $items = [];
    private float $total = 0.0;
    private float $discount = 0.0;
    private float $tax = 0.0;
    private string $couponCode = '';
    private float $updatedAt = 0.0;

    public function __construct(private bool $stamp) {}

    public function addItem(array $item): void {
        $this->items[] = $item;
        if ($this->stamp) $this->updatedAt = microtime(true);
    }

    public function applyCoupon(string $code): void {
        $this->couponCode = $code;
        if ($this->stamp) $this->updatedAt = microtime(true);
    }

    public function calcTotal(): void {
        $t = 0.0;
        foreach ($this->items as $i) $t += $i[1];
        $this->total = $t;
    }

    public function calcDiscount(): void {
        $registry = CouponRegistry::getInstance();
        $this->discount = $this->total * $registry->lookup($this->couponCode);
    }

    public function calcTax(): void {
        $svc = TaxService::getInstance();
        $this->tax = $svc->calculate('NY', $this->total - $this->discount);
    }

    public function grandTotal(): float {
        return $this->total - $this->discount + $this->tax;
    }
}

// Order matters: calcTotal, then calcDiscount, then calcTax. Calling
// calcDiscount first would use a stale total.
function dishonestScenario(array $items, bool $stamp): float {
    $order = new Order($stamp);
    foreach ($items as $it) $order->addItem($it);
    $order->applyCoupon('SAVE10');
    $order->calcTotal();
    $order->calcDiscount();
    $order->calcTax();
    return $order->grandTotal();
}

// ─────────────────────────────────────────────
// Honest: pure functions, flat data
// ─────────────────────────────────────────────

function applyCoupon(array $items, string $code, array $coupons): array {
    $total = 0.0;
    foreach ($items as $i) $total += $i[1];
    $discount = $total * ($coupons[$code] ?? 0.0);
    return [$total, $discount, $total - $discount];
}

function calculateTax(array $order, string $region, array $taxRates): array {
    [$total, $discount, $subtotal] = $order;
    $tax = $subtotal * ($taxRates[$region] ?? 0.0);
    return [$total, $discount, $subtotal, $tax, $subtotal + $tax];
}

function honestScenario(array $items, string $region, array $taxRates,
                        array $coupons): float {
    $result = applyCoupon($items, 'SAVE10', $coupons);
    $final = calculateTax($result, $region, $taxRates);
    return $final[4];
}

// ─────────────────────────────────────────────
// Measurement
// ─────────────────────────────────────────────

function pct(array $values, float $p): int {
    sort($values);
    return (int) $values[(int) ($p * (count($values) - 1))];
}

function distribution(array $values): array {
    return [
        'p05' => pct($values, 0.05), 'p25' => pct($values, 0.25),
        'p50' => pct($values, 0.50), 'p75' => pct($values, 0.75),
        'p95' => pct($values, 0.95),
    ];
}

function batched(callable $f): int {
    $out = [];
    for ($r = 0; $r < REPS; $r++) {
        $t0 = ns();
        for ($i = 0; $i < BATCH; $i++) $GLOBALS['sink'] += $f($i);
        $out[] = intdiv(ns() - $t0, BATCH);
    }
    return pct($out, 0.50);
}

function perIteration(callable $f): array {
    $s = [];
    for ($i = 0; $i < SAMPLES; $i++) {
        $t0 = ns();
        $GLOBALS['sink'] += $f($i);
        $s[] = ns() - $t0;
    }
    return distribution($s);
}

function clockFloor(): int {
    $s = [];
    for ($i = 0; $i < SAMPLES; $i++) {
        $t0 = ns();
        $t1 = ns();
        $s[] = $t1 - $t0;
    }
    return pct($s, 0.50);
}

// ─────────────────────────────────────────────

// Input variants are built once, before measurement.
$inputs = [];
for ($v = 0; $v < 256; $v++) {
    $inputs[] = [['Widget', 29.99 + $v * 0.001], ['Gadget', 39.99],
                 ['Doohickey', 19.99]];
}
$taxRates = ['NY' => 0.08, 'CA' => 0.0725];
$coupons = ['SAVE10' => 0.10];
$region = 'NY';

// Build the singletons once, before measurement.
CouponRegistry::getInstance();
TaxService::getInstance();

$disFull = fn(int $i): float => dishonestScenario($inputs[$i & 255], true);
$disNoStamp = fn(int $i): float => dishonestScenario($inputs[$i & 255], false);
$hon = fn(int $i): float => honestScenario($inputs[$i & 255], $region, $taxRates, $coupons);

for ($i = 0; $i < WARMUP; $i++) {
    $GLOBALS['sink'] += $disFull($i);
    $GLOBALS['sink'] += $disNoStamp($i);
    $GLOBALS['sink'] += $hon($i);
}

$floor = clockFloor();
$bFull = batched($disFull);
$bNoStamp = batched($disNoStamp);
$bHonest = batched($hon);
$dFull = perIteration($disFull);
$dHonest = perIteration($hon);

echo json_encode([
    'language' => 'php',
    'runtime' => 'PHP ' . PHP_VERSION,
    'method' => sprintf('whole scenario, %d iterations between two clock reads, median of %d batches', BATCH, REPS),
    'warmup_iterations' => WARMUP,
    'clock_read_pair_ns' => $floor,
    'batched_ns_per_iteration' => [
        'dishonest_full' => $bFull,
        'dishonest_no_timestamp' => $bNoStamp,
        'honest' => $bHonest,
    ],
    'ratios' => [
        'full_over_honest' => round($bFull / $bHonest, 2),
        'no_timestamp_over_honest' => round($bNoStamp / $bHonest, 2),
    ],
    'per_iteration_ns' => [
        'note' => 'each sample includes one clock_read_pair_ns of instrument cost',
        'dishonest_full' => $dFull,
        'honest' => $dHonest,
    ],
    'sink' => round($GLOBALS['sink'], 3),
], JSON_PRETTY_PRINT), "\n";
