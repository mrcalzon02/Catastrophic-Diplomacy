#!/usr/bin/env bash
# A display must be active. Each revision supplies its own packaged game and artwork.
set -euo pipefail
base=$(cd "${1:?baseline checkout required}" && pwd)
candidate=$(cd "${2:?candidate checkout required}" && pwd)
mkdir -p "${3:?output directory required}"
output=$(cd "$3" && pwd)
temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null LP_NUM_THREADS=2
for revision in base candidate; do
    checkout=${!revision}
    git -C "$checkout" rev-parse HEAD > "$output/$revision.sha"
    gradle -p "$checkout" --no-daemon :desktop:installDist :server:jar
    libs="$checkout/desktop/build/install/desktop/lib"
    test -d "$libs"
    classpath="$libs/*:$checkout/server/build/libs/*"
    if [[ $revision == base ]]; then base_cp=$classpath; else candidate_cp=$classpath; fi
done
desktop_jar=("$candidate"/desktop/build/install/desktop/lib/desktop-*.jar)
unzip -Z1 "${desktop_jar[0]}" | awk '/\.png\.bounds$/' > "$output/laser-tables.txt"
test "$(wc -l < "$output/laser-tables.txt")" -eq 10
mkdir "$temp/fixtures"
javac --release 17 -encoding UTF-8 -cp "$candidate_cp" -d "$temp/fixtures" \
    "$candidate"/desktop/src/test/java/diplomacy/desktop/{DesktopFrameBenchmark,SkyFrameBenchmark,NetworkFrameBenchmark}.java
java -version 2> "$output/java.txt"
lscpu > "$output/cpu.txt"
# Replay a previously captured real 400-ship network snapshot after the timed
# live session. This compares pixels at the same tick across both revisions.
unzip -p "$candidate/docs/benchmarks/2026-11-16/fleet-benchmark-evidence.zip" \
    multiplayer/snapshot.rows > "$temp/snapshot.rows"
(cd "$temp" && sha256sum snapshot.rows) > "$output/snapshot.sha256"
trial() {
    local side=$1 run=$2 scene=$3 main=$4
    shift 4
    local key="$scene-$side-$run" classpath
    if [[ $side == base ]]; then classpath=$base_cp; else classpath=$candidate_cp; fi
    mkdir "$temp/$key"
    (cd "$temp/$key" && java -XX:ActiveProcessorCount=3 -Xms256m -Xmx1200m \
        "$@" -cp "$temp/fixtures:$classpath" "diplomacy.desktop.$main") > "$output/$key.log" 2>&1 || {
        tail -45 "$output/$key.log" >&2; return 1;
    }
    tail -1 "$output/$key.log"
}
# Four pairs of independent JVMs. Reverse which version is measured first in
# alternate pairs. Capture PNGs in the first pair only, after measured GL completion.
for side_run in base-1 candidate-1 candidate-2 base-2 base-3 candidate-3 candidate-4 base-4; do
    side=${side_run%-*}
    run=${side_run#*-}
    extra=()
    if [[ $run == 1 ]]; then extra+=("-Dbench.output=$output/fleet-$side"); fi
    trial "$side" "$run" fleet DesktopFrameBenchmark -Dbench.warm=48 -Dbench.frames=120 "${extra[@]}"
    if (( run <= 2 )); then
        trial "$side" "$run" paused DesktopFrameBenchmark -Dbench.paused=true -Dbench.warm=30 -Dbench.frames=80
        trial "$side" "$run" skirmish DesktopFrameBenchmark -Dbench.engaged=true \
            -Dbench.live=true -Dbench.warm=48 -Dbench.frames=160
        net_image=()
        if (( run == 1 )); then net_image+=("-Dbench.net.output=$output/network-$side.png"); fi
        trial "$side" "$run" network NetworkFrameBenchmark -Dbench.net.ships=16 \
            -Dbench.net.warm=24 -Dbench.net.frames=100 -Dbench.net.proofRows="$temp/snapshot.rows" "${net_image[@]}"
        net_image=()
        if (( run == 1 )); then net_image+=("-Dbench.net.output=$output/network-fleet-$side.png"); fi
        trial "$side" "$run" network-fleet NetworkFrameBenchmark -Dbench.net.ships=200 \
            -Dbench.net.zoom=.40 -Dbench.net.warm=24 -Dbench.net.frames=80 \
            -Dbench.net.proofRows="$temp/snapshot.rows" "${net_image[@]}"
    fi
    if (( run == 1 )); then
        trial "$side" "$run" close DesktopFrameBenchmark -Dbench.zoom=.35 -Dbench.warm=18 \
            -Dbench.frames=80 -Dbench.output="$output/close-$side"
        trial "$side" "$run" sky SkyFrameBenchmark
        trial "$side" "$run" sky-gl20 SkyFrameBenchmark -Dbench.es2=true
    fi
done
python3 "$candidate/tools/performance/summarize-frames.py" "$output" | tee "$output/summary.txt"
