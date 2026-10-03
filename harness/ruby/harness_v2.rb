# Trace harness v2 (Ruby): the Honest Code order scenario.
#
# Changes from v1:
#   1. The whole scenario is timed once per iteration, not six segments on the
#      dishonest side against four on the honest side. Each segment carries one
#      clock read, so six against four charged the dishonest side two extra
#      clock reads before any code ran.
#   2. The primary number is batched: BATCH iterations between two clock reads.
#   3. The singletons are built once, before measurement. v1 reset them inside
#      the loop, which measures construction, not lookup.
#   4. Every result feeds a sink that is printed, and the iteration indexes into
#      a table of input variants built before measurement, so neither side is
#      charged for building its input.
#   5. A dishonest variant without the timestamp writes isolates that cost.
#   6. Percentiles, not a lone median.
#
# The business timestamp uses Time.now, the wall clock production code writes.
# CLOCK_MONOTONIC is used only for measurement.
#
# Run: ruby harness_v2.rb

require 'json'

BATCH = 1000
REPS = 200
WARMUP = 5000
SAMPLES = 5000

def ns
  Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
end

$sink = 0.0

# ─────────────────────────────────────────────
# Dishonest: mutable class, singletons, stamps
# ─────────────────────────────────────────────

class CouponRegistry
  def self.instance
    @instance ||= new
  end

  def lookup(code)
    code == 'SAVE10' ? 0.10 : 0.0
  end
end

class TaxService
  def self.instance
    @instance ||= new
  end

  def calculate(region, taxable)
    taxable * (region == 'NY' ? 0.08 : 0.0725)
  end
end

class Order
  def initialize(stamp)
    @items = []
    @total = 0.0
    @discount = 0.0
    @tax = 0.0
    @coupon_code = ''
    @updated_at = nil
    @stamp = stamp
  end

  def add_item(item)
    @items << item
    recalculate_total
    recalculate_discount
    recalculate_tax
    @updated_at = Time.now if @stamp
  end

  def apply_coupon(code)
    @coupon_code = code
    recalculate_discount
    recalculate_tax
    @updated_at = Time.now if @stamp
  end

  def grand_total
    @total - @discount + @tax
  end

  private

  def recalculate_total
    t = 0.0
    @items.each { |i| t += i[1] }
    @total = t
  end

  def recalculate_discount
    @discount = @total * CouponRegistry.instance.lookup(@coupon_code)
  end

  def recalculate_tax
    @tax = TaxService.instance.calculate('NY', @total - @discount)
  end
end

def dishonest_scenario(items, stamp)
  order = Order.new(stamp)
  items.each { |it| order.add_item(it) }
  order.apply_coupon('SAVE10')
  order.grand_total
end

# ─────────────────────────────────────────────
# Honest: pure functions, flat data
# ─────────────────────────────────────────────

def calculate_order(items, region, tax_rates)
  total = 0.0
  items.each { |i| total += i[1] }
  tax = total * (tax_rates[region] || 0.0)
  [total, tax, total + tax]
end

def apply_coupon(order, code, coupons)
  total, tax, subtotal = order
  discount = total * (coupons[code] || 0.0)
  [total, tax, subtotal, discount, subtotal - discount]
end

def honest_scenario(items, region, tax_rates, coupons)
  result = calculate_order(items, region, tax_rates)
  apply_coupon(result, 'SAVE10', coupons)[4]
end

# ─────────────────────────────────────────────
# Measurement
# ─────────────────────────────────────────────

def pct(values, p)
  s = values.sort
  s[(p * (s.length - 1)).to_i]
end

def distribution(values)
  { 'p05' => pct(values, 0.05), 'p25' => pct(values, 0.25),
    'p50' => pct(values, 0.50), 'p75' => pct(values, 0.75),
    'p95' => pct(values, 0.95) }
end

def batched(&f)
  out = []
  REPS.times do
    t0 = ns
    BATCH.times { |i| $sink += f.call(i) }
    out << ((ns - t0) / BATCH)
  end
  pct(out, 0.50)
end

def per_iteration(&f)
  s = []
  SAMPLES.times do |i|
    t0 = ns
    $sink += f.call(i)
    s << (ns - t0)
  end
  distribution(s)
end

def clock_floor
  s = []
  SAMPLES.times do
    t0 = ns
    t1 = ns
    s << (t1 - t0)
  end
  pct(s, 0.50)
end

# ─────────────────────────────────────────────

# Input variants are built once, before measurement.
inputs = (0...256).map do |v|
  [['Widget', 29.99 + v * 0.001], ['Gadget', 39.99], ['Doohickey', 19.99]]
end
tax_rates = { 'NY' => 0.08, 'CA' => 0.0725 }
coupons = { 'SAVE10' => 0.10 }
region = 'NY'

# Build the singletons once, before measurement.
CouponRegistry.instance
TaxService.instance

dis_full = ->(i) { dishonest_scenario(inputs[i & 255], true) }
dis_nostamp = ->(i) { dishonest_scenario(inputs[i & 255], false) }
hon = ->(i) { honest_scenario(inputs[i & 255], region, tax_rates, coupons) }

WARMUP.times do |i|
  $sink += dis_full.call(i)
  $sink += dis_nostamp.call(i)
  $sink += hon.call(i)
end

floor = clock_floor
b_full = batched { |i| dis_full.call(i) }
b_nostamp = batched { |i| dis_nostamp.call(i) }
b_honest = batched { |i| hon.call(i) }
d_full = per_iteration { |i| dis_full.call(i) }
d_honest = per_iteration { |i| hon.call(i) }

puts JSON.pretty_generate(
  'language' => 'ruby',
  'runtime' => "ruby #{RUBY_VERSION}",
  'method' => "whole scenario, #{BATCH} iterations between two clock reads, " \
              "median of #{REPS} batches",
  'warmup_iterations' => WARMUP,
  'clock_read_pair_ns' => floor,
  'batched_ns_per_iteration' => {
    'dishonest_full' => b_full,
    'dishonest_no_timestamp' => b_nostamp,
    'honest' => b_honest
  },
  'ratios' => {
    'full_over_honest' => (b_full.to_f / b_honest).round(2),
    'no_timestamp_over_honest' => (b_nostamp.to_f / b_honest).round(2)
  },
  'per_iteration_ns' => {
    'note' => 'each sample includes one clock_read_pair_ns of instrument cost',
    'dishonest_full' => d_full,
    'honest' => d_honest
  },
  'sink' => $sink.round(3)
)
