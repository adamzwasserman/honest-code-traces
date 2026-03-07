// Trace harness: captures median nanosecond costs for the six crime
// operations and four rescue operations used in the honestcode.software
// demo engine.
//
// Usage:
//   dotnet run

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text.Json;

const int Warmup = 200;
const int Runs = 1000;

for (int i = 0; i < Warmup; i++) { CrimeRun(); RescueRun(); }

var crimeResults = new long[Runs][];
var rescueResults = new long[Runs][];
for (int i = 0; i < Runs; i++)
{
    crimeResults[i] = CrimeRun();
    rescueResults[i] = RescueRun();
}

var crimeOps = new[] { "call", "field", "calc", "single", "cache", "time" };
var rescueOps = new[] { "call", "arg", "calc", "ret" };

var crime = new Dictionary<string, long>();
for (int op = 0; op < crimeOps.Length; op++)
{
    var col = crimeResults.Select(r => r[op]).ToArray();
    crime[crimeOps[op]] = Median(col);
}

var rescue = new Dictionary<string, long>();
for (int op = 0; op < rescueOps.Length; op++)
{
    var col = rescueResults.Select(r => r[op]).ToArray();
    rescue[rescueOps[op]] = Median(col);
}

var output = new
{
    language = "csharp",
    os = $"{Environment.OSVersion.Platform} {Environment.OSVersion.Version}",
    runtime = $".NET {Environment.Version}",
    runs = Runs,
    crime,
    rescue
};

Console.WriteLine(JsonSerializer.Serialize(output, new JsonSerializerOptions { WriteIndented = true }));

// ═══════════════════════════════════════════════
// Functions
// ═══════════════════════════════════════════════

static long Median(long[] values)
{
    Array.Sort(values);
    return values[values.Length / 2];
}

const int Batch = 100;

static long NsPerBatch(Stopwatch sw) => sw.Elapsed.Ticks * 100 / Batch;

static long[] CrimeRun()
{
    var items = new[] { new Item("Widget", 29.99), new Item("Gadget", 39.99), new Item("Doohickey", 19.99) };
    var sw = new Stopwatch();

    // call: addItem
    sw.Restart();
    for (int b = 0; b < Batch; b++) { var o = new Order(); o.Items.AddRange(items); }
    sw.Stop();
    long callNs = NsPerBatch(sw);

    // field: mutable field write
    sw.Restart();
    for (int b = 0; b < Batch; b++) { var o = new Order(); o.CouponCode = "SAVE10"; o.Discount = 0; }
    sw.Stop();
    long fieldNs = NsPerBatch(sw);

    // calc: computation
    var order = new Order();
    order.Items.AddRange(items);
    sw.Restart();
    for (int b = 0; b < Batch; b++) { order.Total = order.Items.Sum(i => i.Price); }
    sw.Stop();
    long calcNs = NsPerBatch(sw);

    // single: singleton lookup
    sw.Restart();
    for (int b = 0; b < Batch; b++) { CouponRegistry.Reset(); TaxService.Reset(); CouponRegistry.GetInstance(); TaxService.GetInstance(); }
    sw.Stop();
    long singleNs = NsPerBatch(sw) / 2;

    // cache: lookup
    var registry = CouponRegistry.GetInstance();
    sw.Restart();
    for (int b = 0; b < Batch; b++) { var rate = registry.Lookup(order.CouponCode ?? "SAVE10"); order.Discount = order.Total * rate; }
    sw.Stop();
    long cacheNs = NsPerBatch(sw);

    // time: timestamp
    sw.Restart();
    for (int b = 0; b < Batch; b++) { order.UpdatedAt = Stopwatch.GetTimestamp(); }
    sw.Stop();
    long timeNs = NsPerBatch(sw);

    var taxService = TaxService.GetInstance();
    order.Tax = taxService.Calculate("NY", order.Total - order.Discount);

    return new[] { callNs, fieldNs, calcNs, singleNs, cacheNs, timeNs };
}

static long[] RescueRun()
{
    var items = new[] { new Item("Widget", 29.99), new Item("Gadget", 39.99), new Item("Doohickey", 19.99) };
    var taxRates = new Dictionary<string, double> { ["NY"] = 0.08, ["CA"] = 0.0725 };
    var coupons = new Dictionary<string, double> { ["SAVE10"] = 0.10 };
    var sw = new Stopwatch();

    // call: overhead
    sw.Restart();
    for (int b = 0; b < Batch; b++) { sw.ElapsedTicks.ToString(); }
    sw.Stop();
    long callNs = NsPerBatch(sw);

    // arg: passing
    sw.Restart();
    for (int b = 0; b < Batch; b++) { _ = "NY"; }
    sw.Stop();
    long argNs = NsPerBatch(sw);

    // calc: pure computation
    (double, double, double) result = default;
    sw.Restart();
    for (int b = 0; b < Batch; b++) { result = CalculateOrder(items, "NY", taxRates); }
    sw.Stop();
    long calcNs = NsPerBatch(sw);

    // ret: apply coupon
    (string, double, double) final_ = default;
    sw.Restart();
    for (int b = 0; b < Batch; b++) { final_ = ApplyCoupon(result.Item1, result.Item3, "SAVE10", coupons); }
    sw.Stop();
    long retNs = NsPerBatch(sw);

    if (final_.Item3 < 0) throw new Exception("impossible");

    return new[] { callNs, argNs, calcNs, retNs };
}

static (double total, double tax, double subtotal) CalculateOrder(Item[] items, string region, Dictionary<string, double> taxRates)
{
    var total = items.Sum(i => i.Price);
    var tax = total * (taxRates.TryGetValue(region, out var r) ? r : 0);
    return (total, tax, total + tax);
}

static (string code, double discount, double grandTotal) ApplyCoupon(double total, double subtotal, string code, Dictionary<string, double> coupons)
{
    var rate = coupons.TryGetValue(code, out var r) ? r : 0;
    var discount = total * rate;
    return (code, discount, subtotal - discount);
}

// ═══════════════════════════════════════════════
// Types
// ═══════════════════════════════════════════════

record Item(string Name, double Price);


class CouponRegistry
{
    private static CouponRegistry? _instance;
    private static readonly object _lock = new();
    public static CouponRegistry GetInstance()
    {
        if (_instance == null) lock (_lock) { _instance ??= new CouponRegistry(); }
        return _instance;
    }
    public static void Reset() { _instance = null; }
    public double Lookup(string code) => code == "SAVE10" ? 0.10 : 0;
}

class TaxService
{
    private static TaxService? _instance;
    private static readonly object _lock = new();
    public static TaxService GetInstance()
    {
        if (_instance == null) lock (_lock) { _instance ??= new TaxService(); }
        return _instance;
    }
    public static void Reset() { _instance = null; }
    public double Calculate(string region, double taxable) => taxable * 0.08;
}

class Order
{
    public List<Item> Items = new();
    public double Total, Discount, Tax;
    public string? CouponCode;
    public long? UpdatedAt;
}
