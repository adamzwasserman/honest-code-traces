set -e
mkdir -p /tmp/tsbench && cd /tmp/tsbench
sed '$d' /h/typescript/harness_v2.ts > lib.ts
tail -1 /h/typescript/harness_v2.ts | grep -qx "main();"
echo "export { dishonestScenario, honestScenario };" >> lib.ts
cp /h/checks/bench_ts.ts .
npm init -y >/dev/null
npm pkg set type=module >/dev/null
npm install mitata tsx >/dev/null 2>&1
node --version
for m in default: jitless:--jitless noopt:--no-opt; do
  n=${m%%:*}; f=${m#*:}
  node $f --import tsx bench_ts.ts > /out/v8-$n.json 2> /out/v8-$n.err || echo "$n failed"
done
ls -la /out
