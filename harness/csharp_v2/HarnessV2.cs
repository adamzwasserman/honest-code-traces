// Trace harness v2 (C#): the Honest Code order scenario.
//
// Changes from v1:
//   1. The whole scenario is timed once per iteration, not six segments on the
//      dishonest side against four on the honest side. Each segment carries one
//      clock read, so six against four charged the dishonest side two extra
//      clock reads before any code ran. v1 also used Stopwatch batching whose
//      granularity compressed the small differences, which is why C# came out
//      at 1.4x while every other compiled language was higher.
//   2. The primary number is batched: BATCH iterations between two clock reads.
//   3. The singletons are built once, before measurement.
//   4. Results accumulate into a volatile sink that is printed, and the
//      iteration indexes into a table of input variants built before
//      measurement, so RyuJIT cannot hoist the work or eliminate it, and
//      neither side is charged for building its input.
//   5. Dishonest variants isolate the timestamp cost and the list growth.
//   6. Percentiles, not a lone median.
//
// Warmup is 50,000 iterations. Tiered compilation promotes to tier 1 after
// roughly 30 calls plus a background delay, so a short warmup measures tier 0.
//
// A hand-rolled loop cannot fully stop RyuJIT from hoisting loop-invariant
// dictionary lookups. For a figure that will be published, rebuild this on
// BenchmarkDotNet with Consumer/Blackhole and compare.
//
// Run: dotnet run -c Release

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.CompilerServices;

public static class HarnessV2
{
    const int BATCH = 1000;
    const int REPS = 200;
    const int WARMUP = 50_000;
    const int SAMPLES = 5000;

    static double _sinkFlag;
    static double _sink;

    static readonly double NsPerTick = 1_000_000_000.0 / Stopwatch.Frequency;

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    static long Ticks() => Stopwatch.GetTimestamp();

    static long ToNs(long ticks) => (long)(ticks * NsPerTick);

    // ─────────────────────────────────────────
    // Dishonest: mutable class, singletons
    // ─────────────────────────────────────────

    public readonly record struct Item(string Name, double Price);

    sealed class CouponRegistry
    {
        static CouponRegistry _instance;
        public static CouponRegistry Instance => _instance ??= new CouponRegistry();
        public double Lookup(string code) => code == "SAVE10" ? 0.10 : 0.0;
    }

    sealed class TaxService
    {
        static TaxService _instance;
        public static TaxService Instance => _instance ??= new TaxService();
        public double Calculate(string region, double taxable)
            => taxable * (region == "NY" ? 0.08 : 0.0725);
    }

    sealed class Order
    {
        readonly List<Item> _items;
        double _total, _discount, _tax;
        string _couponCode = "";
        DateTime _updatedAt;
        readonly bool _stamp;

        public Order(bool stamp, bool prealloc)
        {
            _stamp = stamp;
            _items = prealloc ? new List<Item>(4) : new List<Item>();
        }

        public void AddItem(Item item)
        {
            _items.Add(item);
            RecalculateTotal();
            RecalculateDiscount();
            RecalculateTax();
            if (_stamp) _updatedAt = DateTime.UtcNow;
        }

        public void ApplyCoupon(string code)
        {
            _couponCode = code;
            RecalculateDiscount();
            RecalculateTax();
            if (_stamp) _updatedAt = DateTime.UtcNow;
        }

        void RecalculateTotal()
        {
            double t = 0;
            foreach (var i in _items) t += i.Price;
            _total = t;
        }

        void RecalculateDiscount()
            => _discount = _total * CouponRegistry.Instance.Lookup(_couponCode);

        void RecalculateTax()
            => _tax = TaxService.Instance.Calculate("NY", _total - _discount);

        public double GrandTotal() => _total - _discount + _tax;
    }

    [MethodImpl(MethodImplOptions.NoInlining)]
    internal static double DishonestScenario(List<Item> items, bool stamp, bool prealloc)
    {
        var order = new Order(stamp, prealloc);
        foreach (var it in items) order.AddItem(it);
        order.ApplyCoupon("SAVE10");
        return order.GrandTotal();
    }

    // ─────────────────────────────────────────
    // Honest: pure functions, flat data
    // ─────────────────────────────────────────

    public readonly record struct OrderResult(double Total, double Tax, double Subtotal);
    public readonly record struct FinalResult(
        double Total, double Tax, double Subtotal, double Discount, double GrandTotal);

    static OrderResult CalculateOrder(
        List<Item> items, string region, Dictionary<string, double> taxRates)
    {
        double total = 0;
        foreach (var i in items) total += i.Price;
        taxRates.TryGetValue(region, out double rate);
        double tax = total * rate;
        return new OrderResult(total, tax, total + tax);
    }

    static FinalResult ApplyCoupon(
        OrderResult o, string code, Dictionary<string, double> coupons)
    {
        coupons.TryGetValue(code, out double rate);
        double discount = o.Total * rate;
        return new FinalResult(o.Total, o.Tax, o.Subtotal, discount,
                               o.Subtotal - discount);
    }

    [MethodImpl(MethodImplOptions.NoInlining)]
    internal static double HonestScenario(
        List<Item> items, string region,
        Dictionary<string, double> taxRates, Dictionary<string, double> coupons)
    {
        var result = CalculateOrder(items, region, taxRates);
        return ApplyCoupon(result, "SAVE10", coupons).GrandTotal;
    }

    // ─────────────────────────────────────────
    // Measurement
    // ─────────────────────────────────────────

    static long Pct(long[] v, double p)
    {
        var s = (long[])v.Clone();
        Array.Sort(s);
        return s[(int)(p * (s.Length - 1))];
    }

    static string Dist(long[] v) => string.Format(
        "{{\"p05\": {0}, \"p25\": {1}, \"p50\": {2}, \"p75\": {3}, \"p95\": {4}}}",
        Pct(v, 0.05), Pct(v, 0.25), Pct(v, 0.50), Pct(v, 0.75), Pct(v, 0.95));

    static long Batched(Func<int, double> f)
    {
        var out_ = new long[REPS];
        for (int r = 0; r < REPS; r++)
        {
            long t0 = Ticks();
            double acc = 0;
            for (int i = 0; i < BATCH; i++) acc += f(i);
            long dt = Ticks() - t0;
            _sink += acc;
            out_[r] = ToNs(dt) / BATCH;
        }
        return Pct(out_, 0.50);
    }

    static long[] PerIteration(Func<int, double> f)
    {
        var s = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++)
        {
            long t0 = Ticks();
            _sink += f(i);
            s[i] = ToNs(Ticks() - t0);
        }
        return s;
    }

    static long ClockFloor()
    {
        var s = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++)
        {
            long t0 = Ticks();
            long t1 = Ticks();
            s[i] = ToNs(t1 - t0);
        }
        return Pct(s, 0.50);
    }

    public static void Main()
    {
        // Input variants are built once, before measurement.
        const int VARIANTS = 256;
        var inputs = new List<List<Item>>(VARIANTS);
        for (int v = 0; v < VARIANTS; v++)
        {
            inputs.Add(new List<Item>
            {
                new Item("Widget", 29.99 + v * 0.001),
                new Item("Gadget", 39.99),
                new Item("Doohickey", 19.99),
            });
        }
        var taxRates = new Dictionary<string, double> { ["NY"] = 0.08, ["CA"] = 0.0725 };
        var coupons = new Dictionary<string, double> { ["SAVE10"] = 0.10 };
        const string region = "NY";

        // Build the singletons once, before measurement.
        _ = CouponRegistry.Instance;
        _ = TaxService.Instance;

        Func<int, double> disFull = i => DishonestScenario(inputs[i & 255], true, false);
        Func<int, double> disNoStamp = i => DishonestScenario(inputs[i & 255], false, false);
        Func<int, double> disPrealloc = i => DishonestScenario(inputs[i & 255], false, true);
        Func<int, double> hon = i => HonestScenario(inputs[i & 255], region, taxRates, coupons);

        double warm = 0;
        for (int i = 0; i < WARMUP; i++)
        {
            warm += disFull(i);
            warm += disNoStamp(i);
            warm += disPrealloc(i);
            warm += hon(i);
        }
        _sink += warm;

        long floor = ClockFloor();
        long bFull = Batched(disFull);
        long bNoStamp = Batched(disNoStamp);
        long bPrealloc = Batched(disPrealloc);
        long bHonest = Batched(hon);
        long[] dFull = PerIteration(disFull);
        long[] dHonest = PerIteration(hon);

        System.Threading.Volatile.Write(ref _sinkFlag, _sink);

        Console.WriteLine("{");
        Console.WriteLine("  \"language\": \"csharp\",");
        Console.WriteLine($"  \"runtime\": \"{Environment.Version}\",");
        Console.WriteLine($"  \"method\": \"whole scenario, {BATCH} iterations between two clock reads, median of {REPS} batches\",");
        Console.WriteLine($"  \"warmup_iterations\": {WARMUP},");
        Console.WriteLine($"  \"stopwatch_frequency_hz\": {Stopwatch.Frequency},");
        Console.WriteLine($"  \"clock_read_pair_ns\": {floor},");
        Console.WriteLine("  \"batched_ns_per_iteration\": {");
        Console.WriteLine($"    \"dishonest_full\": {bFull},");
        Console.WriteLine($"    \"dishonest_no_timestamp\": {bNoStamp},");
        Console.WriteLine($"    \"dishonest_no_timestamp_prealloc\": {bPrealloc},");
        Console.WriteLine($"    \"honest\": {bHonest}");
        Console.WriteLine("  },");
        Console.WriteLine("  \"ratios\": {");
        Console.WriteLine($"    \"full_over_honest\": {(double)bFull / bHonest:F2},");
        Console.WriteLine($"    \"no_timestamp_over_honest\": {(double)bNoStamp / bHonest:F2},");
        Console.WriteLine($"    \"no_timestamp_prealloc_over_honest\": {(double)bPrealloc / bHonest:F2}");
        Console.WriteLine("  },");
        Console.WriteLine("  \"per_iteration_ns\": {");
        Console.WriteLine("    \"note\": \"each sample includes one clock_read_pair_ns of instrument cost\",");
        Console.WriteLine($"    \"dishonest_full\": {Dist(dFull)},");
        Console.WriteLine($"    \"honest\":         {Dist(dHonest)}");
        Console.WriteLine("  },");
        Console.WriteLine($"  \"sink\": {_sink:F3}");
        Console.WriteLine("}");
    }
}
