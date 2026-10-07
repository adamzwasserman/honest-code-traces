// Sums N values held in a contiguous array, in Node, two ways.
function run(idiom, n, reps, f) {
  f();
  const per = [];
  for (let r = 0; r < reps; r++) {
    const t0 = process.hrtime.bigint();
    f();
    per.push(Number(process.hrtime.bigint() - t0) / n);
  }
  per.sort((x, y) => x - y);
  console.log(JSON.stringify({ language: 'javascript', idiom, n,
    ns_per_element_median: +per[Math.floor(per.length / 2)].toFixed(3),
    min: +per[0].toFixed(3), max: +per[per.length - 1].toFixed(3) }));
}
for (const n of [1048576, 33554432]) {
  const f64 = new Float64Array(n);
  for (let i = 0; i < n; i++) f64[i] = i;
  run('loop over Float64Array', n, 5, () => { let s = 0; for (let i = 0; i < n; i++) s += f64[i]; return s; });
  const plain = new Array(n);
  for (let i = 0; i < n; i++) plain[i] = i;
  run('loop over plain Array of integers', n, 5, () => { let s = 0; for (let i = 0; i < n; i++) s += plain[i]; return s; });
}
