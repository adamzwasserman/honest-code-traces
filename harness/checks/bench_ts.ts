// mitata benchmarks over the scenario functions in ../typescript/harness_v2.ts.
// run-checks.sh copies that file to lib.ts, drops its final main() call and
// exports the scenarios. do_not_optimize keeps each result alive.
import { run, bench, do_not_optimize } from "mitata";
import { dishonestScenario, honestScenario } from "./lib.ts";

const inputs = [];
for (let v = 0; v < 256; v++) {
  inputs.push([
    { name: "Widget", price: 29.99 + v * 0.001 },
    { name: "Gadget", price: 39.99 },
    { name: "Doohickey", price: 19.99 },
  ]);
}
const taxRates = { NY: 0.08, CA: 0.0725 };
const coupons = { SAVE10: 0.1 };
let i = 0;

bench("dishonest_full", () => do_not_optimize(dishonestScenario(inputs[i++ & 255], true)));
bench("dishonest_no_timestamp", () => do_not_optimize(dishonestScenario(inputs[i++ & 255], false)));
bench("honest", () => do_not_optimize(honestScenario(inputs[i++ & 255], "NY", taxRates, coupons)));

await run({ format: "json" });
