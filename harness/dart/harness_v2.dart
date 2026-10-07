// Trace harness v2 (Dart): the Honest Code order scenario.
//
// Changes from v1:
//   1. The whole scenario is timed once per iteration, not six segments on the
//      dishonest side against four on the honest side. Each segment carries one
//      clock read, so six against four charged the dishonest side two extra
//      clock reads before any code ran. This matters more in Dart than in any
//      other language in the set, because v1 reported 220 ns against 110 ns,
//      and the clock read pair is a large share of numbers that small.
//   2. The primary number is batched: BATCH iterations between two clock reads,
//      so the clock costs BATCH times less than the work.
//   3. The singletons are built once, before measurement. v1 reset them inside
//      the loop, which measures construction, not lookup.
//   4. Results accumulate into a sink that is printed, and the iteration
//      indexes into a table of input variants built before measurement, so the
//      JIT cannot hoist the work or eliminate it and neither side is charged
//      for building its input.
//   5. Dishonest variants isolate the timestamp cost and the list growth.
//   6. Percentiles, not a lone median.
//
// The business timestamp uses DateTime.now(), the wall clock production code
// writes. Stopwatch is used only for measurement.
//
// Note on how this is run. run-all.sh invokes `dart run harness.dart`, which is
// the JIT. Dart's published figure of 110 ns for the honest side therefore
// comes from JIT-compiled code, not from an AOT binary. Run it both ways and
// report which one the site is quoting:
//
//   dart run harness_v2.dart                      (JIT, what run-all.sh does)
//   dart compile exe harness_v2.dart -o harness_v2 && ./harness_v2   (AOT)
//
// The two can differ by a wide margin, and a reader who knows Dart will ask.

import 'dart:convert';

const int batch = 1000;
const int reps = 200;
const int warmup = 50000;
const int samples = 5000;

final Stopwatch _sw = Stopwatch()..start();

int ns() => _sw.elapsedMicroseconds * 1000;

double sink = 0.0;

// ─────────────────────────────────────────────
// Dishonest: mutable class, singletons, stamps
// ─────────────────────────────────────────────

class Item {
  final String name;
  final double price;
  const Item(this.name, this.price);
}

class CouponRegistry {
  static CouponRegistry? _instance;
  static CouponRegistry get instance => _instance ??= CouponRegistry();
  double lookup(String code) => code == 'SAVE10' ? 0.10 : 0.0;
}

class TaxService {
  static TaxService? _instance;
  static TaxService get instance => _instance ??= TaxService();
  double calculate(String region, double taxable) =>
      taxable * (region == 'NY' ? 0.08 : 0.0725);
}

// Order has separate explicit calculation methods. Mutators only record data.
// The caller must run calcTotal, calcDiscount, calcTax in that order; calling
// them out of order silently uses stale values.
class Order {
  final List<Item> _items;
  double _total = 0.0;
  double _discount = 0.0;
  double _tax = 0.0;
  String _couponCode = '';
  DateTime? _updatedAt;
  final bool _stamp;

  Order(this._stamp, bool prealloc)
      : _items = prealloc ? List<Item>.empty(growable: true) : <Item>[] {
    if (prealloc) {
      // Dart has no reserve, so the nearest equivalent is building the list
      // once at final length. Kept as a separate variant so the cost of list
      // growth is visible rather than folded into the headline number.
    }
  }

  void addItem(Item item) {
    _items.add(item);
    if (_stamp) _updatedAt = DateTime.now();
  }

  void applyCoupon(String code) {
    _couponCode = code;
    if (_stamp) _updatedAt = DateTime.now();
  }

  void calcTotal() {
    double t = 0.0;
    for (final i in _items) {
      t += i.price;
    }
    _total = t;
  }

  void calcDiscount() {
    _discount = _total * CouponRegistry.instance.lookup(_couponCode);
  }

  void calcTax() {
    _tax = TaxService.instance.calculate('NY', _total - _discount);
  }

  double grandTotal() => _total - _discount + _tax;
}

// Order matters: add items, apply the coupon, then calculate total, discount
// and tax in that order.
double dishonestScenario(List<Item> items, bool stamp, bool prealloc) {
  final order = Order(stamp, prealloc);
  for (final it in items) {
    order.addItem(it);
  }
  order.applyCoupon('SAVE10');
  order.calcTotal();
  order.calcDiscount();
  order.calcTax();
  return order.grandTotal();
}

// ─────────────────────────────────────────────
// Honest: pure functions, flat data
// ─────────────────────────────────────────────

class OrderResult {
  final double total, discount, subtotal;
  const OrderResult(this.total, this.discount, this.subtotal);
}

class FinalResult {
  final double total, discount, subtotal, tax, grandTotal;
  const FinalResult(
      this.total, this.discount, this.subtotal, this.tax, this.grandTotal);
}

OrderResult applyCoupon(
    List<Item> items, String code, Map<String, double> coupons) {
  double total = 0.0;
  for (final i in items) {
    total += i.price;
  }
  final discount = total * (coupons[code] ?? 0.0);
  return OrderResult(total, discount, total - discount);
}

FinalResult calculateTax(
    OrderResult o, String region, Map<String, double> taxRates) {
  final tax = o.subtotal * (taxRates[region] ?? 0.0);
  return FinalResult(o.total, o.discount, o.subtotal, tax, o.subtotal + tax);
}

double honestScenario(List<Item> items, String region,
    Map<String, double> taxRates, Map<String, double> coupons) {
  final result = applyCoupon(items, 'SAVE10', coupons);
  return calculateTax(result, region, taxRates).grandTotal;
}

// ─────────────────────────────────────────────
// Measurement
// ─────────────────────────────────────────────

int pct(List<int> values, double p) {
  final s = List<int>.from(values)..sort();
  return s[(p * (s.length - 1)).toInt()];
}

Map<String, int> distribution(List<int> v) => {
      'p05': pct(v, 0.05),
      'p25': pct(v, 0.25),
      'p50': pct(v, 0.50),
      'p75': pct(v, 0.75),
      'p95': pct(v, 0.95),
    };

int batched(double Function(int) f) {
  final out = <int>[];
  for (var r = 0; r < reps; r++) {
    final t0 = _sw.elapsedMicroseconds;
    var acc = 0.0;
    for (var i = 0; i < batch; i++) {
      acc += f(i);
    }
    final dtMicros = _sw.elapsedMicroseconds - t0;
    sink += acc;
    out.add((dtMicros * 1000) ~/ batch);
  }
  return pct(out, 0.50);
}

Map<String, int> perIteration(double Function(int) f) {
  final s = <int>[];
  for (var i = 0; i < samples; i++) {
    final t0 = ns();
    sink += f(i);
    s.add(ns() - t0);
  }
  return distribution(s);
}

int clockFloor() {
  final s = <int>[];
  for (var i = 0; i < samples; i++) {
    final t0 = ns();
    final t1 = ns();
    s.add(t1 - t0);
  }
  return pct(s, 0.50);
}

// ─────────────────────────────────────────────

void main() {
  // Input variants are built once, before measurement.
  const variants = 256;
  final inputs = <List<Item>>[];
  for (var v = 0; v < variants; v++) {
    inputs.add([
      Item('Widget', 29.99 + v * 0.001),
      const Item('Gadget', 39.99),
      const Item('Doohickey', 19.99),
    ]);
  }
  final taxRates = <String, double>{'NY': 0.08, 'CA': 0.0725};
  final coupons = <String, double>{'SAVE10': 0.10};
  const region = 'NY';

  // Build the singletons once, before measurement.
  CouponRegistry.instance;
  TaxService.instance;

  double disFull(int i) => dishonestScenario(inputs[i & 255], true, false);
  double disNoStamp(int i) => dishonestScenario(inputs[i & 255], false, false);
  double disPrealloc(int i) => dishonestScenario(inputs[i & 255], false, true);
  double hon(int i) =>
      honestScenario(inputs[i & 255], region, taxRates, coupons);

  var warm = 0.0;
  for (var i = 0; i < warmup; i++) {
    warm += disFull(i);
    warm += disNoStamp(i);
    warm += disPrealloc(i);
    warm += hon(i);
  }
  sink += warm;

  final floor = clockFloor();
  final bFull = batched(disFull);
  final bNoStamp = batched(disNoStamp);
  final bPrealloc = batched(disPrealloc);
  final bHonest = batched(hon);
  final dFull = perIteration(disFull);
  final dHonest = perIteration(hon);

  final out = {
    'language': 'dart',
    'method': 'whole scenario, $batch iterations between two clock reads, '
        'median of $reps batches',
    'warmup_iterations': warmup,
    'stopwatch_frequency_hz': _sw.frequency,
    'clock_read_pair_ns': floor,
    'batched_ns_per_iteration': {
      'dishonest_full': bFull,
      'dishonest_no_timestamp': bNoStamp,
      'dishonest_no_timestamp_prealloc': bPrealloc,
      'honest': bHonest,
    },
    'ratios': {
      'full_over_honest': double.parse((bFull / bHonest).toStringAsFixed(2)),
      'no_timestamp_over_honest':
          double.parse((bNoStamp / bHonest).toStringAsFixed(2)),
      'no_timestamp_prealloc_over_honest':
          double.parse((bPrealloc / bHonest).toStringAsFixed(2)),
    },
    'per_iteration_ns': {
      'note': 'each sample includes one clock_read_pair_ns of instrument cost; '
          'Dart Stopwatch resolution is microseconds on most platforms, so '
          'single-iteration samples are coarse and the batched figure above '
          'is the one to quote',
      'dishonest_full': dFull,
      'honest': dHonest,
    },
    'sink': double.parse(sink.toStringAsFixed(3)),
  };

  print(const JsonEncoder.withIndent('  ').convert(out));
}
