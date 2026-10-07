<?php
// Raw element access in PHP: the language's own loop reads each element and
// does nothing else. The empty loop has the same shape without the read.
function timed(int $n, int $reps, callable $f): array {
    $f();
    $per = [];
    for ($r = 0; $r < $reps; $r++) {
        $t0 = hrtime(true);
        $f();
        $per[] = (hrtime(true) - $t0) / $n;
    }
    sort($per);
    return [$per[intdiv(count($per), 2)], $per[0], $per[count($per) - 1]];
}
function report(string $idiom, int $n, float $empty, callable $f): void {
    [$m, $lo, $hi] = timed($n, 3, $f);
    echo json_encode(['language' => 'php', 'idiom' => $idiom, 'n' => $n,
        'ns_per_element_median' => round($m, 3), 'empty_loop' => round($empty, 3),
        'net' => round($m - $empty, 3), 'min' => round($lo, 3), 'max' => round($hi, 3)]), "\n";
}
foreach ([1048576, 33554432] as $n) {
    $a = range(0, $n - 1);
    [$empty] = timed($n, 3, function () use ($n) { for ($i = 0; $i < $n; $i++) { } });
    report('$a[$i] in a for loop', $n, $empty, function () use ($a, $n) { for ($i = 0; $i < $n; $i++) { $x = $a[$i]; } });
    [$empty2] = timed($n, 3, function () use ($n) { for ($i = 0; $i < $n; $i++) { } });
    report('foreach over a packed array', $n, $empty2, function () use ($a) { foreach ($a as $x) { } });
    unset($a);
}
