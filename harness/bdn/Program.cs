using System.Collections.Generic;
using BenchmarkDotNet.Attributes;
using BenchmarkDotNet.Configs;
using BenchmarkDotNet.Jobs;
using BenchmarkDotNet.Running;

// BenchmarkDotNet over the same scenario functions the hand-written C#
// harness times. BenchmarkDotNet runs its own warm-up and tiering checks,
// and each benchmark returns its double so the JIT cannot drop the work.
// Two jobs: the .NET default, and tiered compilation off like the harness.
public class CsBench
{
    List<List<HarnessV2.Item>> inputs = new();
    Dictionary<string, double> taxRates = new() { ["NY"] = 0.08, ["CA"] = 0.0725 };
    Dictionary<string, double> coupons = new() { ["SAVE10"] = 0.10 };
    int next;

    [GlobalSetup]
    public void Setup()
    {
        for (int v = 0; v < 256; v++)
        {
            inputs.Add(new List<HarnessV2.Item>
            {
                new HarnessV2.Item("Widget", 29.99 + v * 0.001),
                new HarnessV2.Item("Gadget", 39.99),
                new HarnessV2.Item("Doohickey", 19.99),
            });
        }
    }

    [Benchmark] public double DishonestFull() => HarnessV2.DishonestScenario(inputs[next++ & 255], true, false);
    [Benchmark] public double DishonestNoTimestamp() => HarnessV2.DishonestScenario(inputs[next++ & 255], false, false);
    [Benchmark] public double DishonestPrealloc() => HarnessV2.DishonestScenario(inputs[next++ & 255], false, true);
    [Benchmark] public double Honest() => HarnessV2.HonestScenario(inputs[next++ & 255], "NY", taxRates, coupons);
}

public static class BdnProgram
{
    public static void Main(string[] args)
    {
        var config = DefaultConfig.Instance
            .AddJob(Job.Default.WithId("tiered-default"))
            .AddJob(Job.Default.WithId("tiered-off").WithEnvironmentVariable("DOTNET_TieredCompilation", "0"));
        BenchmarkRunner.Run<CsBench>(config);
    }
}
