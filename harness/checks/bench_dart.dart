// benchmark_harness checks over the scenario functions in ../dart/harness_v2.dart.
// run-checks.sh copies that file to lib.dart with main renamed. exercise() calls
// run() once, so measure() returns microseconds per scenario call.
import 'package:benchmark_harness/benchmark_harness.dart';
import 'lib.dart';

class ScenarioBench extends BenchmarkBase {
  ScenarioBench(super.name, this.f);

  final double Function(int) f;
  int i = 0;
  double acc = 0;

  @override
  void run() {
    acc += f(i++);
  }

  @override
  void exercise() => run();
}

void main() {
  final inputs = <List<Item>>[
    for (var v = 0; v < 256; v++)
      [Item('Widget', 29.99 + v * 0.001), const Item('Gadget', 39.99), const Item('Doohickey', 19.99)],
  ];
  final taxRates = {'NY': 0.08, 'CA': 0.0725};
  final coupons = {'SAVE10': 0.10};
  final benches = [
    ScenarioBench('dishonest_full', (i) => dishonestScenario(inputs[i & 255], true, false)),
    ScenarioBench('dishonest_no_timestamp', (i) => dishonestScenario(inputs[i & 255], false, false)),
    ScenarioBench('honest', (i) => honestScenario(inputs[i & 255], 'NY', taxRates, coupons)),
  ];
  for (var rep = 0; rep < 10; rep++) {
    for (final b in benches) {
      print('${b.name} ${(b.measure() * 1000).toStringAsFixed(1)}');
    }
  }
}
