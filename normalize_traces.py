"""Scale the per-step times in traces/landing-page.json so each language's
dishonest and honest totals equal the figures in harness/medians_v2.json.

The dishonest total leaves out the timestamp writes, so the two timestamp
steps carry no time. The relative width of the other steps still comes from
the old harness. The animation is an illustration, and only the two totals
are measurements. The output goes to traces/landing-page-v2.json and nothing
is overwritten.

Run: uv run python normalize_traces.py
"""

import json
from pathlib import Path

HERE = Path(__file__).parent
SOURCE = HERE / "traces" / "landing-page.json"
MEDIANS = HERE / "harness" / "medians_v2.json"
OUTPUT = HERE / "traces" / "landing-page-v2.json"

# A step measured at under 1 ns gets this width on screen. "ns" keeps the
# measured value, and only the animation reads "animNs".
NOMINAL_NS = 1.0

# The surprise pairs dishonest code in the first language with honest code in the second.
SURPRISE_PAIR = ("go", "typescript")
LABELS = {"go": "Go", "swift": "Swift", "cpp": "C++", "typescript": "TypeScript"}


def scale_steps(steps, target_total, uncharged_ops=()):
    weights = [0 if s["op"] in uncharged_ops else s["ns"] for s in steps]
    factor = target_total / sum(weights)
    scaled = [{**s, "ns": round(w * factor, 2)} for s, w in zip(steps, weights)]
    drift = round(target_total - sum(s["ns"] for s in scaled), 2)
    widest = max(range(len(scaled)), key=lambda i: scaled[i]["ns"])
    scaled[widest]["ns"] = round(scaled[widest]["ns"] + drift, 2)
    for step in scaled:
        step["animNs"] = max(step["ns"], NOMINAL_NS)
    return scaled


def normalize_language(entry, crime_total, rescue_total):
    return {
        **entry,
        "crime": scale_steps(entry["crime"], crime_total, uncharged_ops=("time",)),
        "rescue": scale_steps(entry["rescue"], rescue_total),
        "crimeTotal": crime_total,
        "rescueTotal": rescue_total,
    }


def main():
    source = json.loads(SOURCE.read_text())
    medians = json.loads(MEDIANS.read_text())
    output = {}
    for lang, entry in source.items():
        if lang == "surprise":
            continue
        measured = medians[lang]
        output[lang] = normalize_language(entry, measured["dishonest_ns"], measured["honest_ns"])
    # A cross-language pairing uses the conservative end of every figure: the
    # smallest dishonest time we measured for the first language and the largest
    # honest time we measured for the second.
    crime_lang, rescue_lang = SURPRISE_PAIR
    crime_total = min(c["dishonest_ns"] for c in medians[crime_lang]["candidates"].values())
    rescue_total = max(c["honest_ns"] for c in medians[rescue_lang]["candidates"].values())
    crime_steps = scale_steps(source[crime_lang]["crime"], crime_total, uncharged_ops=("time",))
    rescue_steps = scale_steps(source[rescue_lang]["rescue"], rescue_total)
    output["surprise"] = {
        **source["surprise"],
        "crimeLabel": f"\u2620 Dishonest \u2014 {LABELS[crime_lang]} Order class (compiled ahead of time)",
        "rescueLabel": f"\u2726 Honest \u2014 Pure functions ({LABELS[rescue_lang]}, V8)",
        "crime": crime_steps,
        "rescue": rescue_steps,
        "crimeTotal": crime_total,
        "rescueTotal": rescue_total,
    }
    OUTPUT.write_text(json.dumps(output, indent=2))

    print(f"{'Language':<12}{'Dishonest':>11}{'Honest':>9}{'Ratio':>8}")
    for lang, data in output.items():
        print(f"{lang:<12}{data['crimeTotal']:>9.1f}ns{data['rescueTotal']:>7.1f}ns{data['crimeTotal'] / data['rescueTotal']:>7.1f}x")
    surprise = output["surprise"]
    print(f"\nSurprise: dishonest {SURPRISE_PAIR[0]} {surprise['crimeTotal']:.1f} ns against honest {SURPRISE_PAIR[1]} {surprise['rescueTotal']:.1f} ns, {surprise['crimeTotal'] / surprise['rescueTotal']:.1f}x")

main()
