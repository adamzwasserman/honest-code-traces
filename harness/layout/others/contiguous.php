<?php
// Sums N integers held in a contiguous (packed) array, in PHP, two ways.
function run(string $idiom, int $n, int $reps, callable $f): void {
    $f();
    $per = [];
    for ($r = 0; $r < $reps; $r++) {
        $t0 = hrtime(true);
        $f();
        $per[] = (hrtime(true) - $t0) / $n;
    }
    sort($per);
    echo json_encode(['language' => 'php', 'idiom' => $idiom, 'n' => $n,
        'ns_per_element_median' => round($per[intdiv(count($per), 2)], 3),
        'min' => round($per[0], 3), 'max' => round($per[count($per) - 1], 3)]), "\n";
}
foreach ([1048576, 33554432] as $n) {
    $a = range(0, $n - 1);
    run('foreach over packed array', $n, 3, function () use ($a) { $s = 0; foreach ($a as $x) { $s += $x; } return $s; });
    run('array_sum', $n, 5, function () use ($a) { return array_sum($a); });
    unset($a);
}
