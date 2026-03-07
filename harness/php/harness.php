<?php
/**
 * Trace harness: captures median nanosecond costs for the six crime
 * operations and four rescue operations used in the honestcode.software
 * demo engine.
 *
 * Usage:
 *   php harness.php
 */

define('WARMUP', 200);
define('RUNS', 1000);

// ═══════════════════════════════════════════════
// CRIME SCENE: mutable class with singletons
// ═══════════════════════════════════════════════

class CouponRegistry {
    private static ?CouponRegistry $instance = null;
    public static function getInstance(): self {
        if (self::$instance === null) self::$instance = new self();
        return self::$instance;
    }
    public static function reset(): void { self::$instance = null; }
    public function lookup(string $code): float {
        return $code === 'SAVE10' ? 0.10 : 0;
    }
}

class TaxService {
    private static ?TaxService $instance = null;
    public static function getInstance(): self {
        if (self::$instance === null) self::$instance = new self();
        return self::$instance;
    }
    public static function reset(): void { self::$instance = null; }
    public function calculate(string $region, float $taxable): float {
        return $taxable * 0.08;
    }
}

class Order {
    public array $items = [];
    public float $total = 0;
    public float $discount = 0;
    public float $tax = 0;
    public ?string $couponCode = null;
    public ?float $updatedAt = null;
}

function ns(): int {
    return (int)(hrtime(true));
}

function crimeRun(): array {
    $order = new Order();
    $items = [
        ['name' => 'Widget', 'price' => 29.99],
        ['name' => 'Gadget', 'price' => 39.99],
        ['name' => 'Doohickey', 'price' => 19.99],
    ];

    $t0 = ns();
    $order->items = array_merge($order->items, $items);
    $callNs = ns() - $t0;

    $t0 = ns();
    $order->couponCode = 'SAVE10';
    $order->discount = 0;
    $fieldNs = ns() - $t0;

    $t0 = ns();
    $order->total = array_sum(array_column($order->items, 'price'));
    $calcNs = ns() - $t0;

    CouponRegistry::reset();
    TaxService::reset();
    $t0 = ns();
    $registry = CouponRegistry::getInstance();
    $taxService = TaxService::getInstance();
    $singleNs = intdiv(ns() - $t0, 2);

    $t0 = ns();
    $rate = $registry->lookup($order->couponCode);
    $order->discount = $order->total * $rate;
    $cacheNs = ns() - $t0;

    $t0 = ns();
    $order->updatedAt = hrtime(true);
    $timeNs = ns() - $t0;

    $order->tax = $taxService->calculate('NY', $order->total - $order->discount);

    return [$callNs, $fieldNs, $calcNs, $singleNs, $cacheNs, $timeNs];
}

// ═══════════════════════════════════════════════
// RESCUE: pure functions, flat data
// ═══════════════════════════════════════════════

function calculateOrder(array $items, string $region, array $taxRates): array {
    $total = array_sum(array_column($items, 'price'));
    $tax = $total * ($taxRates[$region] ?? 0);
    return ['total' => $total, 'tax' => $tax, 'subtotal' => $total + $tax];
}

function applyCoupon(array $order, string $code, array $coupons): array {
    $rate = $coupons[$code] ?? 0;
    $discount = $order['total'] * $rate;
    return array_merge($order, [
        'coupon_code' => $code,
        'discount' => $discount,
        'grand_total' => $order['subtotal'] - $discount,
    ]);
}

function rescueRun(): array {
    $items = [
        ['name' => 'Widget', 'price' => 29.99],
        ['name' => 'Gadget', 'price' => 39.99],
        ['name' => 'Doohickey', 'price' => 19.99],
    ];
    $taxRates = ['NY' => 0.08, 'CA' => 0.0725];
    $coupons = ['SAVE10' => 0.10];

    $t0 = ns();
    $t1 = ns();
    $callNs = $t1 - $t0;

    $t0 = ns();
    $region = 'NY';
    $argNs = ns() - $t0;

    $t0 = ns();
    $result = calculateOrder($items, $region, $taxRates);
    $calcNs = ns() - $t0;

    $t0 = ns();
    $final = applyCoupon($result, 'SAVE10', $coupons);
    $retNs = ns() - $t0;

    assert($final['grand_total'] > 0);

    return [$callNs, $argNs, $calcNs, $retNs];
}

// ═══════════════════════════════════════════════
// HARNESS
// ═══════════════════════════════════════════════

function median(array $values): float {
    sort($values);
    return $values[intdiv(count($values), 2)];
}

for ($i = 0; $i < WARMUP; $i++) { crimeRun(); rescueRun(); }

$crimeResults = [];
$rescueResults = [];
for ($i = 0; $i < RUNS; $i++) {
    $crimeResults[] = crimeRun();
    $rescueResults[] = rescueRun();
}

$crimeOps = ['call', 'field', 'calc', 'single', 'cache', 'time'];
$rescueOps = ['call', 'arg', 'calc', 'ret'];

$crime = [];
foreach ($crimeOps as $idx => $op) {
    $col = array_column($crimeResults, $idx);
    $crime[$op] = median($col);
}

$rescue = [];
foreach ($rescueOps as $idx => $op) {
    $col = array_column($rescueResults, $idx);
    $rescue[$op] = median($col);
}

echo json_encode([
    'language' => 'php',
    'os' => PHP_OS . ' ' . php_uname('r'),
    'runtime' => 'PHP ' . PHP_VERSION,
    'runs' => RUNS,
    'crime' => $crime,
    'rescue' => $rescue,
], JSON_PRETTY_PRINT) . "\n";
