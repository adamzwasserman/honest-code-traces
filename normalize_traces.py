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
    crime_lang, rescue_lang = "cpp", "typescript"
    output["surprise"] = {
        **source["surprise"],
        "crimeLabel": "☠ Dishonest — C++ Order class (compiled ahead of time)",
        "rescueLabel": "✦ Honest — Pure functions (TypeScript, V8)",
        "crime": output[crime_lang]["crime"],
        "rescue": output[rescue_lang]["rescue"],
        "crimeTotal": output[crime_lang]["crimeTotal"],
        "rescueTotal": output[rescue_lang]["rescueTotal"],
    }
    OUTPUT.write_text(json.dumps(output, indent=2))

    print(f"{'Language':<12}{'Dishonest':>11}{'Honest':>9}{'Ratio':>8}")
    for lang, data in output.items():
        print(f"{lang:<12}{data['crimeTotal']:>9.1f}ns{data['rescueTotal']:>7.1f}ns{data['crimeTotal'] / data['rescueTotal']:>7.1f}x")
    ts_honest = output["typescript"]["rescueTotal"]
    print(f"\nHonest TypeScript takes {ts_honest:.1f} ns. Dishonest code in:")
    for lang in sorted(["cpp", "swift", "go", "java", "kotlin", "csharp"], key=lambda k: output[k]["crimeTotal"]):
        total = output[lang]["crimeTotal"]
        verdict = f"{total / ts_honest:.1f}x slower" if total > ts_honest else f"{ts_honest / total:.1f}x FASTER than honest TypeScript"
        print(f"  {lang:<8}{total:>7.1f} ns, {verdict}")


main()
