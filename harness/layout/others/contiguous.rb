# Sums N integers held in a contiguous Array, in Ruby, two ways.
require 'json'

def run(idiom, n, reps)
  yield
  per = []
  reps.times do
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    yield
    per << (Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond) - t0).to_f / n
  end
  per.sort!
  puts JSON.generate(language: 'ruby', idiom: idiom, n: n, ns_per_element_median: per[per.size / 2].round(3), min: per.first.round(3), max: per.last.round(3))
  $stdout.flush
end

[1_048_576, 33_554_432].each do |n|
  a = Array.new(n) { |i| i }
  run('each loop over Array', n, 3) { s = 0; a.each { |x| s += x }; s }
  run('Array#sum', n, 5) { a.sum }
  a = nil
  GC.start
end
