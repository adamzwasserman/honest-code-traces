import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

// JMH benchmark over the same scenario functions the hand-written Java
// harness times. Run by run-jmh.sh. JMH feeds each returned double to a
// Blackhole, so the JIT cannot drop the work.
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class JavaBench {
    List<List<HarnessV2.Item>> inputs = new ArrayList<>();
    Map<String, Double> taxRates = Map.of("NY", 0.08, "CA", 0.0725);
    Map<String, Double> coupons = Map.of("SAVE10", 0.10);
    int next;

    @Setup
    public void setup() {
        for (int v = 0; v < 256; v++) {
            inputs.add(List.of(
                new HarnessV2.Item("Widget", 29.99 + v * 0.001),
                new HarnessV2.Item("Gadget", 39.99),
                new HarnessV2.Item("Doohickey", 19.99)));
        }
    }

    @Benchmark
    public double dishonestFull() {
        return HarnessV2.dishonestScenario(inputs.get(next++ & 255), true, false);
    }

    @Benchmark
    public double dishonestNoTimestamp() {
        return HarnessV2.dishonestScenario(inputs.get(next++ & 255), false, false);
    }

    @Benchmark
    public double dishonestPrealloc() {
        return HarnessV2.dishonestScenario(inputs.get(next++ & 255), false, true);
    }

    @Benchmark
    public double honest() {
        return HarnessV2.honestScenario(inputs.get(next++ & 255), "NY", taxRates, coupons);
    }
}
