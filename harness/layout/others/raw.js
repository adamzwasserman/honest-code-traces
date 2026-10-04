// Raw element access in Node: the language's own loop reads each element and
// stores it, and does nothing else. The empty loop has the same shape without the read.
let sink = 0;
function timed(n, reps, f) {
  f();
  const per = [];
  for (let r = 0; r < reps; r++) {
    const t0 = process.hrtime.bigint();
    f();
    per.push(Number(process.hrtime.bigint() - t0) / n);
  }
  per.sort((x, y) => x - y);
  return [per[Math.floor(per.length / 2)], per[0], per[per.length - 1]];
}
function report(idiom, n, empty, f) {
  const [m, lo, hi] = timed(n, 5, f);
  console.log(JSON.stringify({ language: 'javascript', idiom, n, ns_per_element_median: +m.toFixed(3),
    empty_loop: +empty.toFixed(3), net: +(m - empty).toFixed(3), min: +lo.toFixed(3), max: +hi.toFixed(3) }));
}
for (const n of [1048576, 33554432]) {
  const f64 = new Float64Array(n);
  for (let i = 0; i < n; i++) f64[i] = i;
  const [empty] = timed(n, 5, () => { let s = 0; for (let i = 0; i < n; i++) s = i; sink = s; });
  report('loop over Float64Array', n, empty, () => { let x = 0; for (let i = 0; i < n; i++) x = f64[i]; sink = x; });
}
