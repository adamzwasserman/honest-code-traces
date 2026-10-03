"""Scale the per-step times in traces/landing-page.json so each language's
dishonest and honest totals equal the v2 medians in harness/medians_v2.json.

The relative width of each step still comes from the old harness, which put
a clock read inside every step. Only the totals are v2 measurements. The
output goes to traces/landing-page-v2.json and nothing is overwritten.

Run: uv run python normalize_traces.py
"""

import json
from pathlib import Path

HERE = Path(__file__).parent
SOURCE = HERE / "traces" / "landing-page.json"
MEDIANS = HERE / "harness" / "medians_v2.json"
OUTPUT = HERE / "traces" / "landing-page-v2.json"


def scale_steps(steps, target_total):
    old_total = sum(s["ns"] for s in steps)
    factor = target_total / old_total
    scaled = [{**s, "ns": round(s["ns"] * factor, 2)} for s in steps]
    drift = round(target_total - sum(s["ns"] for s in scaled), 2)
    widest = max(range(len(scaled)), key=lambda i: scaled[i]["ns"])
    scaled[widest]["ns"] = round(scaled[widest]["ns"] + drift, 2)
    return scaled


def normalize_language(entry, crime_total, rescue_total):
    crime = scale_steps(entry["crime"], crime_total)
    rescue = scale_steps(entry["rescue"], rescue_total)
    return {
        **entry,
        "crime": crime,
        "rescue": rescue,
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
        output[lang] = normalize_language(
            entry, measured["dishonest_full_ns"], measured["honest_ns"]
        )
    surprise = source["surprise"]
    crime_lang, rescue_lang = "typescript", "python"
    output["surprise"] = {
        **surprise,
        "crime": output[crime_lang]["crime"],
        "rescue": output[rescue_lang]["rescue"],
        "crimeTotal": output[crime_lang]["crimeTotal"],
        "rescueTotal": output[rescue_lang]["rescueTotal"],
    }
    OUTPUT.write_text(json.dumps(output, indent=2))
    print(f"{'Language':<12}{'Crime':>9}{'Rescue':>9}{'Ratio':>8}   was")
    for lang, data in output.items():
        old = source[lang]
        print(
            f"{lang:<12}{data['crimeTotal']:>7.0f}ns{data['rescueTotal']:>7.0f}ns"
            f"{data['crimeTotal'] / data['rescueTotal']:>7.1f}x"
            f"   {old['crimeTotal']}/{old['rescueTotal']} = {old['crimeTotal'] / old['rescueTotal']:.1f}x"
        )


main()
