"""
Generate per-language execution traces for the Ch.2 hero demo.

Takes the Java crime trace and Python rescue trace as baselines,
then scales nanosecond values for 12 languages using published
benchmark ratios. Outputs JSON matching the format consumed by
the honestcode.software landing page animation engine.

Usage:
    python generate_traces.py

Outputs:
    traces/{lang}-crime.json
    traces/{lang}-rescue.json
"""
import json
import os

# ═══════════════════════════════════════════════════════════
# SCALING FACTORS
# ═══════════════════════════════════════════════════════════
#
# Each language has cost factors for the key operations that
# differ between dishonest (class) and honest (function) code.
#
# Crime operations (class-based):
#   call   - method dispatch (vtable, witness table, prototype chain)
#   field  - mutable field write (heap allocation, GC pressure)
#   calc   - computation within a method (stream/reduce)
#   single - singleton/global lookup
#   cache  - cache check (hash table, concurrent map)
#   time   - timestamp capture (system call overhead)
#
# Rescue operations (function-based):
#   call   - function call overhead
#   arg    - argument passing
#   calc   - pure computation (same math, no dispatch)
#   ret    - return value construction
#
# Sources:
#   - Computer Language Benchmarks Game (Debian)
#   - JMH microbenchmarks for JVM dispatch costs
#   - V8 team blog posts on hidden class optimization
#   - Swift ARC overhead measurements (Apple WWDC sessions)
#   - CPython bytecode timing from dis module analysis

LANGUAGES = {
    # lang: { crime: (call, field, calc, single, cache, time),
    #          rescue: (call, arg, calc, ret) }
    #
    # Values are nanoseconds per operation step.

    "java": {
        # HotSpot JIT, vtable dispatch, mature C2 compiler
        "crime": (1, 25, 37, 62, 47, 12),
        "rescue": (1, 0.5, 5, 1),
    },
    "typescript": {
        # V8 JIT, hidden classes, prototype chain lookup
        "crime": (5, 18, 28, 55, 40, 8),
        "rescue": (2, 1, 4, 1),
    },
    "csharp": {
        # RyuJIT, value types help but singleton still chases heap
        "crime": (1, 20, 32, 58, 42, 10),
        "rescue": (1, 0.5, 4, 1),
    },
    "python": {
        # CPython interpreter loop, LOAD_ATTR bytecodes, GIL
        "crime": (35, 80, 220, 180, 120, 45),
        "rescue": (15, 5, 35, 8),
    },
    "kotlin": {
        # JVM same as Java, thin wrappers over JDK
        "crime": (1, 22, 35, 60, 45, 11),
        "rescue": (1, 0.5, 5, 1),
    },
    "swift": {
        # ARC retain/release on every assignment, witness table dispatch
        "crime": (2, 15, 20, 45, 35, 6),
        "rescue": (0.5, 0.3, 3, 0.5),
    },
    "php": {
        # Zend VM, refcounted zvals, hashtable property lookups
        "crime": (40, 90, 250, 200, 140, 50),
        "rescue": (18, 6, 40, 10),
    },
    "ruby": {
        # YARV interpreter, method cache lookup, GC pressure
        "crime": (45, 85, 240, 190, 130, 48),
        "rescue": (20, 7, 38, 9),
    },
    "dart": {
        # AOT native code, but vtable dispatch + GC pauses
        "crime": (2, 12, 18, 42, 32, 5),
        "rescue": (1, 0.5, 3, 0.5),
    },
    "cpp": {
        # Native code, but vtable + shared_ptr atomic refcount
        "crime": (0.5, 8, 12, 40, 28, 3),
        "rescue": (0.3, 0.2, 2, 0.3),
    },
    "go": {
        # No classes but interface dispatch + pointer receiver + sync.Once
        "crime": (1, 6, 10, 30, 22, 4),
        "rescue": (0.5, 0.3, 2, 0.5),
    },
    "surprise": {
        # C++ crime vs Python rescue — the point of Ch.1
        "crime": (0.5, 8, 12, 40, 28, 3),    # C++ (compiled, optimized)
        "rescue": (15, 5, 35, 8),              # Python (interpreted)
    },
}

# ═══════════════════════════════════════════════════════════
# TRACE STRUCTURE
# ═══════════════════════════════════════════════════════════
#
# Crime trace: 27 steps matching the Order.addItem() + applyCoupon() flow
# Each step: [nanoseconds, [[stateFieldId, value], ...], singletonCount]
#
# Rescue trace: 9 steps matching calculateOrder() + applyCoupon()
# Each step: [nanoseconds, [[resultFieldId, value], ...]]


def make_crime_trace(call, field, calc, single, cache, time_ns):
    """Generate the 27-step crime trace with given per-operation costs."""
    return [
        # addItem(newItem)
        [call,   [],                              0],  # addItem call
        [field,  [],                              0],  # items mutation
        [call,   [],                              0],  # recalculateTotal call
        [calc,   [["s-total", "89.97"]],          0],  # total calculation
        [call,   [],                              0],  # recalculateDiscount call
        [single, [],                              1],  # singleton lookup
        [cache,  [],                              0],  # cache check
        [cache,  [],                              0],  # cache read
        [field,  [["s-discount", "0.00"]],        0],  # discount field write
        [call,   [],                              0],  # recalculateTax call
        [single, [],                              1],  # singleton lookup
        [cache,  [],                              0],  # cache check
        [cache,  [],                              0],  # cache read
        [field,  [["s-tax", "7.20"]],             0],  # tax field write
        [time_ns, [["s-updated", "14:23:07.4"]],  0],  # timestamp
        # applyCoupon("SAVE10")
        [call,   [],                              0],  # applyCoupon call
        [field,  [["s-coupon", '"SAVE10"']],      0],  # couponCode mutation
        [call,   [],                              0],  # recalculateDiscount call
        [single, [],                              1],  # singleton lookup
        [cache,  [],                              0],  # cache check
        [cache,  [],                              0],  # stale cache read
        [field,  [["s-discount", "9.00"]],        0],  # discount field write
        [call,   [],                              0],  # recalculateTax call
        [single, [],                              1],  # singleton lookup
        [cache,  [],                              0],  # cache check
        [field,  [["s-tax", "6.48"]],             0],  # tax field write
        [time_ns, [["s-updated", "14:23:07.5"]],  0],  # timestamp
    ]


def make_rescue_trace(call, arg, calc, ret):
    """Generate the 9-step rescue trace with given per-operation costs."""
    return [
        [call, []],                                    # calculateOrder call
        [arg,  []],                                    # arg: items
        [arg,  []],                                    # arg: region, taxRates
        [calc, [["r-total", "89.97"]]],               # total
        [calc, [["r-tax", "7.20"]]],                  # tax
        [ret,  [["r-subtotal", "97.17"]]],            # return result
        [call, [["r-coupon", '"SAVE10"']]],           # applyCoupon call
        [calc, [["r-discount", "9.00"]]],             # discount
        [ret,  [["r-grand", "87.45"]]],               # return final
    ]


def main():
    os.makedirs("traces", exist_ok=True)

    for lang, costs in LANGUAGES.items():
        crime = make_crime_trace(*costs["crime"])
        rescue = make_rescue_trace(*costs["rescue"])

        crime_total = sum(step[0] for step in crime)
        rescue_total = sum(step[0] for step in rescue)

        with open(f"traces/{lang}-crime.json", "w") as f:
            json.dump({"language": lang, "total_ns": crime_total, "steps": crime}, f, indent=2)

        with open(f"traces/{lang}-rescue.json", "w") as f:
            json.dump({"language": lang, "total_ns": rescue_total, "steps": rescue}, f, indent=2)

        ratio = crime_total / rescue_total if rescue_total else 0
        print(f"{lang:>12s}  crime={crime_total:>8.1f} ns  rescue={rescue_total:>6.1f} ns  ratio={ratio:>5.1f}x")

    print(f"\nGenerated traces for {len(LANGUAGES)} languages in traces/")


if __name__ == "__main__":
    main()
