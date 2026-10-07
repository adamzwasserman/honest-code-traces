// Experiment 1 of preregistration/memory-layout.md, in Java.
// Sums one field across N records in five layouts and prints one JSON line
// per layout and size: nanoseconds per element, median of five repetitions.
// Java has no array of structs, so L1 is one long[] with four fields per
// record and L2 is four long[] arrays.
// Build and run: javac LayoutSweep.java && java -Xmx10g LayoutSweep
// Set LAYOUT_MAX_N to stop at a smaller size for a quick check.
import java.util.Arrays;
import java.util.Random;

public class LayoutSweep {
    static final class Rec { long a, b, c, d; Rec(long a) { this.a = a; b = 1; c = 2; d = 3; } }
    static final class Node { long a, b, c, d; Node next; Node(long a) { this.a = a; b = 1; c = 2; d = 3; } }

    interface Traversal { long run(int t); }

    static volatile long sink;

    static void measure(String layout, int n, long bytes, Traversal f) {
        long target = 64L << 20;
        long loops = Math.max(1, target / n);
        for (int w = 0; w < 5; w++) sink = f.run(w);
        double[] per = new double[5];
        for (int r = 0; r < 5; r++) {
            long t0 = System.nanoTime();
            long sum = 0;
            for (long l = 0; l < loops; l++) sum += f.run((int) l);
            long t1 = System.nanoTime();
            sink = sum;
            per[r] = (double) (t1 - t0) / ((double) loops * n);
        }
        Arrays.sort(per);
        System.out.printf("{\"language\":\"java\",\"layout\":\"%s\",\"n\":%d,\"bytes\":%d,"
            + "\"ns_per_element_median\":%.3f,\"min\":%.3f,\"max\":%.3f}%n",
            layout, n, bytes, per[2], per[0], per[4]);
    }

    public static void main(String[] args) {
        int maxN = 33554432;
        String e = System.getenv("LAYOUT_MAX_N");
        if (e != null) maxN = Integer.parseInt(e);
        Random rng = new Random(42);
        for (int n : new int[] {512, 32768, 1048576, 33554432}) {
            if (n > maxN) break;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i;
            for (int i = n - 1; i > 0; i--) { int j = rng.nextInt(i + 1); int t = order[i]; order[i] = order[j]; order[j] = t; }

            {   // L1: one flat array, four fields per record
                long[] v = new long[n * 4];
                for (int i = 0; i < n; i++) { v[i * 4] = i; v[i * 4 + 1] = 1; v[i * 4 + 2] = 2; v[i * 4 + 3] = 3; }
                measure("L1_array_of_structs", n, (long) n * 32, t -> {
                    v[0] += t & 1;
                    long s = 0;
                    for (int i = 0; i < n; i++) s += v[i * 4];
                    return s;
                });
            }
            {   // L2: struct of arrays
                long[] a = new long[n], b = new long[n], c = new long[n], d = new long[n];
                for (int i = 0; i < n; i++) { a[i] = i; b[i] = 1; c[i] = 2; d[i] = 3; }
                measure("L2_struct_of_arrays", n, (long) n * 32, t -> {
                    a[0] += t & 1;
                    long s = 0;
                    for (int i = 0; i < n; i++) s += a[i];
                    return s;
                });
            }
            {   // L3, L4: heap objects allocated in order, visited in order and in random order
                Rec[] p = new Rec[n];
                for (int i = 0; i < n; i++) p[i] = new Rec(i);
                System.gc();
                measure("L3_heap_objects_in_order", n, (long) n * (48 + 4), t -> {
                    p[0].a += t & 1;
                    long s = 0;
                    for (int i = 0; i < n; i++) s += p[i].a;
                    return s;
                });
                Rec[] q = new Rec[n];
                for (int i = 0; i < n; i++) q[i] = p[order[i]];
                measure("L4_heap_objects_random_order", n, (long) n * (48 + 4), t -> {
                    q[0].a += t & 1;
                    long s = 0;
                    for (int i = 0; i < n; i++) s += q[i].a;
                    return s;
                });
            }
            {   // L5: linked list whose nodes were allocated in order and linked at random
                Node[] nodes = new Node[n];
                for (int i = 0; i < n; i++) nodes[i] = new Node(i);
                for (int k = 0; k + 1 < n; k++) nodes[order[k]].next = nodes[order[k + 1]];
                Node head = nodes[order[0]];
                nodes = null;
                System.gc();
                final Node h = head;
                measure("L5_linked_list_random", n, (long) n * 56, t -> {
                    h.a += t & 1;
                    long s = 0;
                    for (Node x = h; x != null; x = x.next) s += x.a;
                    return s;
                });
            }
        }
    }
}
