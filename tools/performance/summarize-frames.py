"""Validate independent source builds and summarize whole-client frame samples."""
import csv
import io
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops

folder = Path(sys.argv[1])


def data(scene, side, run):
    lines = (folder / f"{scene}-{side}-{run}.log").read_text().splitlines()
    headers = next(line for line in lines if line.startswith("frame,"))
    rows = [line for line in lines if line[:1].isdigit() and "," in line]
    return lines, list(csv.DictReader(io.StringIO("\n".join([headers, *rows]))))


def quantiles(values):
    values = sorted(values)
    return " / ".join(f"{values[math.ceil(p * len(values)) - 1]:.2f}"
                      for p in (.50, .95, .99))


for scene, runs, count, metric in (("fleet", 4, 120, "complete_ms"), ("skirmish", 2, 160, "interval_ms"),
                                  ("paused", 2, 80, "complete_ms")):
    starts = set()
    ends = set()
    for side in ("base", "candidate"):
        pooled = []
        draw_calls, texture_binds = [], []
        stationary = 0
        for run in range(1, runs + 1):
            lines, rows = data(scene, side, run)
            starts.add(next(line for line in lines if line.startswith("BEGIN ")))
            ends.add(next(line for line in lines if line.startswith("END ")))
            assert len(rows) == count, (scene, side, run, len(rows))
            values = [float(row[metric]) for row in rows]
            print(f"{scene} {side} {run}: {metric} p50/95/99 {quantiles(values)} ms")
            pooled.extend(values)
            draw_calls.extend(int(row["draws"]) for row in rows)
            texture_binds.extend(int(row["binds"]) for row in rows)
            stationary += sum(row["stationary_camera"] == "true" for row in rows)
        assert all(x >= 0 for x in draw_calls + texture_binds), "Headed OpenGL profiler was not enabled"
        print(f"{scene} {side} OpenGL draws p50/p95/p99 {quantiles(draw_calls)}; "
              f"texture binds p50/p95/p99 {quantiles(texture_binds)}")
        print(f"{scene} {side} pooled: {quantiles(pooled)} ms; over 16.67 / 33.33 ms "
              f"{sum(n > 16.67 for n in pooled)} / {sum(n > 33.33 for n in pooled)} of {len(pooled)}; "
              f"stationary camera {stationary}/{len(pooled)}")
    assert len(starts) == 1, starts
    if scene in ("fleet", "paused"):
        assert len(ends) == 1, ends

for scene, runs, count in (("network", 2, 100), ("network-fleet", 2, 80)):
    for side in ("base", "candidate"):
        durations, cpu, draw_calls, texture_binds = [], [], [], []
        for run in range(1, runs + 1):
            lines, rows = data(scene, side, run)
            assert any(line.startswith("PASS renderedTicks=") and "acks=1/1" in line for line in lines)
            assert len(rows) == count
            assert int(rows[-1]["rendered_tick"]) > int(rows[0]["rendered_tick"])
            durations.extend(float(row["interval_ms"]) for row in rows)
            cpu.extend(float(row["render_thread_cpu_ms"]) for row in rows)
            draw_calls.extend(int(row["draws"]) for row in rows)
            texture_binds.extend(int(row["binds"]) for row in rows)
        assert all(x >= 0 for x in draw_calls + texture_binds), "Headed network OpenGL profiler was not enabled"
        print(f"{scene} {side}: OpenGL draws p50/p95/p99 {quantiles(draw_calls)}; "
              f"texture binds p50/p95/p99 {quantiles(texture_binds)}")
        print(f"{scene} {side}: frame intervals {quantiles(durations)} ms; render thread CPU "
              f"{quantiles(cpu)} ms; over 16.67 / 33.33 ms {sum(x > 16.67 for x in durations)} / "
              f"{sum(x > 33.33 for x in durations)} of {len(durations)}")

for scene in ("sky", "sky-gl20"):
    _, original = data(scene, "base", 1)
    _, changed = data(scene, "candidate", 1)
    assert len(original) == len(changed) == 192
    fields = ("frame", "level", "motion", "width", "height", "rgba_crc32")
    for before, after in zip(original, changed):
        assert all(before[k] == after[k] for k in fields), (scene, before, after)
    assert len({row["width"] for row in original}) == 2
    print(f"{scene}: all {len(original)} complete RGBA readbacks match across revisions")
    for motion in ("stationary", "pan"):
        for side, rows in (("base", original), ("candidate", changed)):
            times = [float(r["complete_ms"]) for r in rows if r["motion"] == motion]
            print(f"{scene} {motion} {side}: {quantiles(times)} ms")

captures = sorted((folder / "fleet-base").glob("*.png"))
assert len(captures) == 2
pairs = [(image, folder / "fleet-candidate" / image.name) for image in captures]
close = sorted((folder / "close-base").glob("*.png"))
assert len(close) == 2
pairs.extend((image, folder / "close-candidate" / image.name) for image in close)
pairs.extend((folder / f"{view}-base.png", folder / f"{view}-candidate.png")
             for view in ("network", "network-fleet"))
for image, candidate in pairs:
    with Image.open(image) as left, Image.open(candidate) as right:
        assert left.size == right.size == (1280, 720)
        diff = ImageChops.difference(left.convert("RGBA"), right.convert("RGBA"))
        maxima = [band.getextrema()[1] for band in diff.split()]
        channels = diff.split()
        worst = channels[0]
        for channel in channels[1:]:
            worst = ImageChops.lighter(worst, channel)
        counts = worst.histogram()
        print(f"{image.relative_to(folder)}: {sum(counts[1:])}/{left.width * left.height} pixels changed; "
              f"{sum(counts[3:])} differ by >2/255; {sum(counts[9:])} by >8/255; max RGBA {maxima}")
