# Trace harness: captures median nanosecond costs for the six crime
# operations and four rescue operations used in the honestcode.software
# demo engine.
#
# Usage:
#   ruby harness.rb

require 'json'

WARMUP = 200
RUNS = 1000

# ═══════════════════════════════════════════════
# CRIME SCENE: mutable class with singletons
# ═══════════════════════════════════════════════

class CouponRegistry
  @@instance = nil
  def self.get_instance
    @@instance ||= new
  end
  def self.reset!
    @@instance = nil
  end
  def lookup(code)
    code == "SAVE10" ? 0.10 : 0
  end
end

class TaxService
  @@instance = nil
  def self.get_instance
    @@instance ||= new
  end
  def self.reset!
    @@instance = nil
  end
  def calculate(region, taxable)
    taxable * 0.08
  end
end

class Order
  attr_accessor :items, :total, :discount, :tax, :coupon_code, :updated_at
  def initialize
    @items = []
    @total = 0.0
    @discount = 0.0
    @tax = 0.0
    @coupon_code = nil
    @updated_at = nil
  end
end

def ns
  Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
end

def crime_run
  order = Order.new
  items = [
    { name: "Widget", price: 29.99 },
    { name: "Gadget", price: 39.99 },
    { name: "Doohickey", price: 19.99 },
  ]

  t0 = ns
  order.items.concat(items)
  call_ns = ns - t0

  t0 = ns
  order.coupon_code = "SAVE10"
  order.discount = 0.0
  field_ns = ns - t0

  t0 = ns
  order.total = order.items.sum { |i| i[:price] }
  calc_ns = ns - t0

  CouponRegistry.reset!
  TaxService.reset!
  t0 = ns
  registry = CouponRegistry.get_instance
  tax_service = TaxService.get_instance
  single_ns = (ns - t0) / 2

  t0 = ns
  rate = registry.lookup(order.coupon_code)
  order.discount = order.total * rate
  cache_ns = ns - t0

  t0 = ns
  order.updated_at = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
  time_ns = ns - t0

  order.tax = tax_service.calculate("NY", order.total - order.discount)

  [call_ns, field_ns, calc_ns, single_ns, cache_ns, time_ns]
end

# ═══════════════════════════════════════════════
# RESCUE: pure functions, flat data
# ═══════════════════════════════════════════════

def calculate_order(items, region, tax_rates)
  total = items.sum { |i| i[:price] }
  tax = total * (tax_rates[region] || 0)
  { total: total, tax: tax, subtotal: total + tax }
end

def apply_coupon(order, code, coupons)
  rate = coupons[code] || 0
  discount = order[:total] * rate
  order.merge(coupon_code: code, discount: discount, grand_total: order[:subtotal] - discount)
end

def rescue_run
  items = [
    { name: "Widget", price: 29.99 },
    { name: "Gadget", price: 39.99 },
    { name: "Doohickey", price: 19.99 },
  ]
  tax_rates = { "NY" => 0.08, "CA" => 0.0725 }
  coupons = { "SAVE10" => 0.10 }

  t0 = ns
  t1 = ns
  call_ns = t1 - t0

  t0 = ns
  region = "NY"
  arg_ns = ns - t0

  t0 = ns
  result = calculate_order(items, region, tax_rates)
  calc_ns = ns - t0

  t0 = ns
  final_ = apply_coupon(result, "SAVE10", coupons)
  ret_ns = ns - t0

  raise "impossible" if final_[:grand_total] < 0

  [call_ns, arg_ns, calc_ns, ret_ns]
end

# ═══════════════════════════════════════════════
# HARNESS
# ═══════════════════════════════════════════════

def median(values)
  sorted = values.sort
  sorted[sorted.length / 2]
end

WARMUP.times { crime_run; rescue_run }

crime_results = RUNS.times.map { crime_run }
rescue_results = RUNS.times.map { rescue_run }

crime_ops = %w[call field calc single cache time]
rescue_ops = %w[call arg calc ret]

crime = {}
crime_ops.each_with_index do |op, i|
  col = crime_results.map { |r| r[i] }
  crime[op] = median(col)
end

rescue_ = {}
rescue_ops.each_with_index do |op, i|
  col = rescue_results.map { |r| r[i] }
  rescue_[op] = median(col)
end

puts JSON.pretty_generate({
  language: "ruby",
  os: "#{RUBY_PLATFORM}",
  runtime: "#{RUBY_ENGINE} #{RUBY_VERSION}",
  runs: RUNS,
  crime: crime,
  rescue: rescue_,
})
