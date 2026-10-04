# benchmark-ips checks over the scenario functions in ../ruby/harness_v2.rb.
# run-checks.sh copies that file's declarations to lib.rb, without its
# top-level measurement code, and runs this script ten times.
require 'benchmark/ips'
require_relative 'lib'

inputs = (0...256).map do |v|
  [['Widget', 29.99 + v * 0.001], ['Gadget', 39.99], ['Doohickey', 19.99]]
end
tax_rates = { 'NY' => 0.08, 'CA' => 0.0725 }
coupons = { 'SAVE10' => 0.10 }
i = 0
sink = 0.0

Benchmark.ips do |x|
  x.config(time: 5, warmup: 2)
  x.report('dishonest_full') { i += 1; sink += dishonest_scenario(inputs[i & 255], true) }
  x.report('dishonest_no_timestamp') { i += 1; sink += dishonest_scenario(inputs[i & 255], false) }
  x.report('honest') { i += 1; sink += honest_scenario(inputs[i & 255], 'NY', tax_rates, coupons) }
end
puts "sink #{sink}"
