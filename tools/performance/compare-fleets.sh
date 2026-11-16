#!/usr/bin/env bash
# Run after :core:processResources. Compare two source trees on one Java 17 machine.
set -euo pipefail
baseline=$(cd "${1:?baseline checkout}" && pwd)
candidate=$(cd "${2:?candidate checkout}" && pwd)
results=${3:?empty output directory}
mkdir -p "$results"
results=$(cd "$results" && pwd)
build_dir=$(mktemp -d)
trap 'rm -rf -- "$build_dir"' EXIT
for variant in baseline candidate; do
    source_root=${!variant}
    mkdir -p "$build_dir/$variant"
    find "$source_root/core/src/main/java" "$source_root/server/src/main/java" -name '*.java' > "$build_dir/$variant/sources"
    javac --release 17 -encoding UTF-8 -d "$build_dir/$variant" @"$build_dir/$variant/sources"
done
resources="$candidate/core/build/resources/main"
test -s "$resources/hardpoints/approved-v1.txt"
(cd "$resources" && sha256sum hardpoints/approved-v1.txt) > "$results/hardpoint-digest.txt"
git -C "$baseline" rev-parse HEAD > "$results/baseline.txt"
git -C "$candidate" rev-parse HEAD > "$results/candidate.txt"
mkdir -p "$build_dir/probes"
javac --release 17 -cp "$build_dir/candidate" -d "$build_dir/probes" "$candidate"/tools/performance/*.java
vm=(-XX:ActiveProcessorCount=2 -Xms640m -Xmx640m -XX:+UseSerialGC)
for weather in clear asteroids; do
    java "${vm[@]}" -cp "$build_dir/probes" PairedCpuProfile "$build_dir/baseline" "$build_dir/candidate" \
        "$resources" "$candidate/core/src/main/resources" "$results/$weather.csv" "$weather" | tee "$results/$weather.log"
done
for trial in baseline-1 candidate-1 candidate-2 baseline-2; do
    variant=${trial%-*}
    java "${vm[@]}" -cp "$build_dir/probes:$build_dir/$variant:$resources" LanProbe 200 135 > "$results/$trial-lan.csv"
    tail -1 "$results/$trial-lan.csv"
done
