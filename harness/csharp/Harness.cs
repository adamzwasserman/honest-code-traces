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

static long[] CrimeRun()
{
    var order = new Order();
    var items = new[] { new Item("Widget", 29.99), new Item("Gadget", 39.99), new Item("Doohickey", 19.99) };
    var sw = new Stopwatch();

    sw.Restart(); order.Items.AddRange(items); sw.Stop();
    long callNs = sw.Elapsed.Ticks * 100;

    sw.Restart(); order.CouponCode = "SAVE10"; order.Discount = 0; sw.Stop();
    long fieldNs = sw.Elapsed.Ticks * 100;

    sw.Restart(); order.Total = order.Items.Sum(i => i.Price); sw.Stop();
    long calcNs = sw.Elapsed.Ticks * 100;

    CouponRegistry.Reset(); TaxService.Reset();
    sw.Restart();
    var registry = CouponRegistry.GetInstance();
    var taxService = TaxService.GetInstance();
    sw.Stop();
    long singleNs = sw.Elapsed.Ticks * 100 / 2;

    sw.Restart();
    var rate = registry.Lookup(order.CouponCode!);
    order.Discount = order.Total * rate;
    sw.Stop();
    long cacheNs = sw.Elapsed.Ticks * 100;

    sw.Restart(); order.UpdatedAt = Stopwatch.GetTimestamp(); sw.Stop();
    long timeNs = sw.Elapsed.Ticks * 100;

    order.Tax = taxService.Calculate("NY", order.Total - order.Discount);

    return new[] { callNs, fieldNs, calcNs, singleNs, cacheNs, timeNs };
}

static long[] RescueRun()
{
    var items = new[] { new Item("Widget", 29.99), new Item("Gadget", 39.99), new Item("Doohickey", 19.99) };
    var taxRates = new Dictionary<string, double> { ["NY"] = 0.08, ["CA"] = 0.0725 };
    var coupons = new Dictionary<string, double> { ["SAVE10"] = 0.10 };
    var sw = new Stopwatch();

    sw.Restart(); sw.Stop();
    long callNs = sw.Elapsed.Ticks * 100;

    sw.Restart(); var region = "NY"; sw.Stop();
    long argNs = sw.Elapsed.Ticks * 100;
    _ = region;

    sw.Restart(); var result = CalculateOrder(items, "NY", taxRates); sw.Stop();
    long calcNs = sw.Elapsed.Ticks * 100;

    sw.Restart(); var final_ = ApplyCoupon(result, "SAVE10", coupons); sw.Stop();
    long retNs = sw.Elapsed.Ticks * 100;

    if (final_.GrandTotal < 0) throw new Exception("impossible");

    return new[] { callNs, argNs, calcNs, retNs };
}

static OrderResult CalculateOrder(Item[] items, string region, Dictionary<string, double> taxRates)
{
    var total = items.Sum(i => i.Price);
    var tax = total * (taxRates.TryGetValue(region, out var r) ? r : 0);
    return new(total, tax, total + tax);
}

static FinalResult ApplyCoupon(OrderResult order, string code, Dictionary<string, double> coupons)
{
    var rate = coupons.TryGetValue(code, out var r) ? r : 0;
    var discount = order.Total * rate;
    return new(order.Total, order.Tax, order.Subtotal, code, discount, order.Subtotal - discount);
}

// ═══════════════════════════════════════════════
// Types
// ═══════════════════════════════════════════════

record Item(string Name, double Price);

record OrderResult(double Total, double Tax, double Subtotal);
record FinalResult(double Total, double Tax, double Subtotal, string CouponCode, double Discount, double GrandTotal);

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
