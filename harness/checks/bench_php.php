<?php
// PHPBench checks over the scenario functions in ../php/harness_v2.php.
// run-checks.sh copies that file's declarations to lib.php, without its
// top-level measurement code.
require_once __DIR__ . '/lib.php';

class ScenarioBench
{
    private array $inputs = [];
    private array $taxRates = ['NY' => 0.08, 'CA' => 0.0725];
    private array $coupons = ['SAVE10' => 0.10];
    private int $next = 0;
    private float $sink = 0.0;

    public function __construct()
    {
        for ($v = 0; $v < 256; $v++) {
            $this->inputs[] = [['Widget', 29.99 + $v * 0.001], ['Gadget', 39.99], ['Doohickey', 19.99]];
        }
    }

    public function benchDishonestFull(): void
    {
        $this->sink += dishonestScenario($this->inputs[$this->next++ & 255], true);
    }

    public function benchDishonestNoTimestamp(): void
    {
        $this->sink += dishonestScenario($this->inputs[$this->next++ & 255], false);
    }

    public function benchHonest(): void
    {
        $this->sink += honestScenario($this->inputs[$this->next++ & 255], 'NY', $this->taxRates, $this->coupons);
    }
}
