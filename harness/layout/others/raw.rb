# Raw element access in Ruby: the language's own loop reads each element and
# does nothing else. The empty loop has the same shape without the read.
require 'json'

def timed(n, reps)
  yield
  per = []
  reps.times do
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    yield
    per << (Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond) - t0).to_f / n
  end
  per.sort!
  [per[per.size / 2], per.first, per.last]
end

def report(idiom, n, empty, &loop)
  m, lo, hi = timed(n, 3, &loop)
  puts JSON.generate(language: 'ruby', idiom: idiom, n: n, ns_per_element_median: m.round(3),
                     empty_loop: empty.round(3), net: (m - empty).round(3), min: lo.round(3), max: hi.round(3))
  $stdout.flush
end

[1_048_576, 33_554_432].each do |n|
  a = Array.new(n) { |i| i }
  empty, = timed(n, 3) { i = 0; while i < n; i += 1; end }
  report('a[i] in a while loop', n, empty) { i = 0; while i < n; x = a[i]; i += 1; end }
  empty2, = timed(n, 3) { n.times { |i| } }
  report('Array#each', n, empty2) { a.each { |x| } }
  a = nil
  GC.start
end
