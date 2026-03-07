// Trace harness: captures median nanosecond costs for the six crime
// operations and four rescue operations used in the honestcode.software
// demo engine.
//
// Uses Stopwatch (monotonic) and batched operations (100 iterations per
// measurement) to overcome timer granularity.
//
// Usage:
//   dart run harness.dart

import 'dart:io';

const warmup = 200;
const runs = 1000;
const batch = 100;

// ═══════════════════════════════════════════════
// CRIME SCENE: mutable class with singletons
// ═══════════════════════════════════════════════

class CouponRegistry {
  static CouponRegistry? _instance;
  static CouponRegistry getInstance() {
    _instance ??= CouponRegistry();
    return _instance!;
  }
  static void reset() => _instance = null;
  double lookup(String code) => code == 'SAVE10' ? 0.10 : 0;
}

class TaxService {
  static TaxService? _instance;
  static TaxService getInstance() {
    _instance ??= TaxService();
    return _instance!;
  }
  static void reset() => _instance = null;
  double calculate(String region, double taxable) => taxable * 0.08;
}

class Item {
  final String name;
  final double price;
  Item(this.name, this.price);
}

class Order {
  List<Item> items = [];
  double total = 0;
  double discount = 0;
  double tax = 0;
  String? couponCode;
  int? updatedAt;
}

int swNs(Stopwatch sw) => sw.elapsedMicroseconds * 1000;

List<int> crimeRun() {
  final items = [Item('Widget', 29.99), Item('Gadget', 39.99), Item('Doohickey', 19.99)];
  final sw = Stopwatch();

  // call: append items
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) {
    final order = Order();
    order.items.addAll(items);
  }
  sw.stop();
  final callNs = swNs(sw) ~/ batch;

  // field: mutable field write
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) {
    final order = Order();
    order.couponCode = 'SAVE10';
    order.discount = 0;
  }
  sw.stop();
  final fieldNs = swNs(sw) ~/ batch;

  // calc: computation
  final order = Order();
  order.items = items;
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) {
    order.total = order.items.fold(0.0, (sum, item) => sum + item.price);
  }
  sw.stop();
  final calcNs = swNs(sw) ~/ batch;

  // single: singleton lookup
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) {
    CouponRegistry.reset();
    TaxService.reset();
    CouponRegistry.getInstance();
    TaxService.getInstance();
  }
  sw.stop();
  final singleNs = swNs(sw) ~/ batch ~/ 2;

  // cache: lookup
  final registry = CouponRegistry.getInstance();
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) {
    final rate = registry.lookup('SAVE10');
    order.discount = order.total * rate;
  }
  sw.stop();
  final cacheNs = swNs(sw) ~/ batch;

  // time: timestamp
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) {
    order.updatedAt = DateTime.now().microsecondsSinceEpoch;
  }
  sw.stop();
  final timeNs = swNs(sw) ~/ batch;

  final taxService = TaxService.getInstance();
  order.tax = taxService.calculate('NY', order.total - order.discount);

  return [callNs, fieldNs, calcNs, singleNs, cacheNs, timeNs];
}

// ═══════════════════════════════════════════════
// RESCUE: pure functions, flat data
// ═══════════════════════════════════════════════

(double total, double tax, double subtotal) calculateOrder(List<Item> items, String region, Map<String, double> taxRates) {
  final total = items.fold(0.0, (sum, item) => sum + item.price);
  final tax = total * (taxRates[region] ?? 0);
  return (total, tax, total + tax);
}

(String code, double discount, double grandTotal) applyCoupon(double total, double subtotal, String code, Map<String, double> coupons) {
  final rate = coupons[code] ?? 0;
  final discount = total * rate;
  return (code, discount, subtotal - discount);
}

List<int> rescueRun() {
  final items = [Item('Widget', 29.99), Item('Gadget', 39.99), Item('Doohickey', 19.99)];
  final taxRates = {'NY': 0.08, 'CA': 0.0725};
  final coupons = {'SAVE10': 0.10};
  final sw = Stopwatch();

  // call: function call overhead
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) { items.length; }
  sw.stop();
  final callNs = swNs(sw) ~/ batch;

  // arg: passing
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) { 'NY'; }
  sw.stop();
  final argNs = swNs(sw) ~/ batch;

  // calc: pure computation
  late (double, double, double) result;
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) {
    result = calculateOrder(items, 'NY', taxRates);
  }
  sw.stop();
  final calcNs = swNs(sw) ~/ batch;

  // ret: apply coupon
  late (String, double, double) finalResult;
  sw.reset(); sw.start();
  for (var i = 0; i < batch; i++) {
    finalResult = applyCoupon(result.$1, result.$3, 'SAVE10', coupons);
  }
  sw.stop();
  final retNs = swNs(sw) ~/ batch;

  assert(finalResult.$3 > 0);

  return [callNs, argNs, calcNs, retNs];
}

// ═══════════════════════════════════════════════
// HARNESS
// ═══════════════════════════════════════════════

int median(List<int> values) {
  values.sort();
  return values[values.length ~/ 2];
}

void main() {
  for (var i = 0; i < warmup; i++) { crimeRun(); rescueRun(); }

  final crimeResults = List.generate(runs, (_) => crimeRun());
  final rescueResults = List.generate(runs, (_) => rescueRun());

  final crimeOps = ['call', 'field', 'calc', 'single', 'cache', 'time'];
  final rescueOps = ['call', 'arg', 'calc', 'ret'];

  final crime = <String, int>{};
  for (var i = 0; i < crimeOps.length; i++) {
    final col = crimeResults.map((r) => r[i]).toList();
    crime[crimeOps[i]] = median(col);
  }

  final rescue = <String, int>{};
  for (var i = 0; i < rescueOps.length; i++) {
    final col = rescueResults.map((r) => r[i]).toList();
    rescue[rescueOps[i]] = median(col);
  }

  print('{');
  print('  "language": "dart",');
  print('  "os": "${Platform.operatingSystem} ${Platform.operatingSystemVersion}",');
  print('  "runtime": "Dart ${Platform.version.split(' ').first}",');
  print('  "runs": $runs,');
  print('  "crime": {');
  for (var i = 0; i < crimeOps.length; i++) {
    final comma = i < crimeOps.length - 1 ? ',' : '';
    print('    "${crimeOps[i]}": ${crime[crimeOps[i]]}$comma');
  }
  print('  },');
  print('  "rescue": {');
  for (var i = 0; i < rescueOps.length; i++) {
    final comma = i < rescueOps.length - 1 ? ',' : '';
    print('    "${rescueOps[i]}": ${rescue[rescueOps[i]]}$comma');
  }
  print('  }');
  print('}');
}
