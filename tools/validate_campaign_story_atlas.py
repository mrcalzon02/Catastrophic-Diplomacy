#!/usr/bin/env python3
"""Optional authored campaign-map/story diagnostic (not an executable release gate)."""
import csv
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "docs" / "campaign"
ATLAS = BASE / "ATLAS_500.json"
SCRIPTS = BASE / "scripts"
FACTIONS = [
    "TERRAN", "DRAXIAN", "CARNELIAN", "ZIPPLEDROW", "UZIPT", "MANDRALLY",
    "VAERITH_PRISM", "ZELTHAR_LUMINARI", "AUREATE_SYNOD", "NYX_BIOLITH",
    "UMBRA_FOUNDRY",
]
CAP_ANCHORS = [
    (1, 1), (10, 2), (25, 4), (50, 8), (75, 12), (100, 18),
    (125, 26), (150, 36), (175, 47), (200, 60), (225, 74),
    (250, 88), (275, 102), (300, 116), (325, 129), (350, 142),
    (375, 153), (400, 164), (425, 175), (450, 184), (475, 193),
    (490, 198), (493, 199), (494, 200), (500, 200),
]
OPERATIONS = [
    "Patrol", "Interdiction", "Escort screen", "Convoy battle", "Torpedo raid",
    "Strike wing", "Siege line", "Breakthrough", "Command fleet", "Fleet action",
]
FINALE = [
    "The Needle Gate", "The Broken Crown", "Graveyard of Delegates",
    "The Furnace Ring", "The Silent Chancery", "The Last Armada", "Article Zero",
]


def cap(mission):
    for (a, x), (b, y) in zip(CAP_ANCHORS, CAP_ANCHORS[1:]):
        if mission <= b:
            step = b - a
            return x + ((mission - a) * (y - x) + step // 2) // step
    return 200


def verify():
    data = json.loads(ATLAS.read_text(encoding="utf-8"))
    regions, fronts, nodes = (data[key] for key in ("regions", "fronts", "nodes"))
    assert (len(regions), len(fronts), len(nodes)) == (10, 50, 500)
    assert data["schema_version"] == 1
    titles = set()
    previous_cap = 0
    for index, node in enumerate(nodes):
        mission = index + 1
        sector = (mission - 1) // 50 + 1
        front_index = (mission - 1) // 10
        front_in_sector = ((mission - 1) % 50) // 10
        slot = (mission - 1) % 10
        operation = (
            "Sector capital" if mission % 50 == 0 else
            OPERATIONS[(slot + front_in_sector + (sector - 1) * 2) % 10]
        )
        assert node["id"] == mission
        assert (node["sector"], node["front"]) == (sector, front_index + 1)
        assert node["title"] not in titles
        titles.add(node["title"])
        assert node["route_previous"] == (mission - 1 if mission > 1 else None)
        assert node["route_next"] == (mission + 1 if mission < 500 else None)
        for target in node["side_routes"]:
            assert 1 <= target <= 500
            assert (target - 1) // 10 == front_index
            assert mission in nodes[target - 1]["side_routes"]
        assert node["opponent"] == FACTIONS[(sector * 2 + front_index) % len(FACTIONS)]
        assert node["enemy_tier"] == sector - 1
        assert node["operation"] == operation
        assert node["fleet_cap"] == cap(mission)
        assert 1 <= node["fleet_cap"] <= 200
        assert node["fleet_cap"] >= previous_cap
        previous_cap = node["fleet_cap"]
        assert node["waves"] == (1 if mission >= 494 else 3 + (mission - 1) // 50)
        assert node["script_key"] == "CD-M" + str(mission).zfill(3)
        assert node["reveal_on_clear"] is not None if slot == 9 else node["reveal_on_clear"] is None
        assert node["live_victory_condition"] == "destroy all mandatory hostile waves"
    assert [node["title"] for node in nodes[493:]] == FINALE
    assert all(node["fleet_cap"] == 200 and node["waves"] == 1 for node in nodes[493:])
    assert sum(len(node["side_routes"]) for node in nodes) // 2 == 250
    for i, front in enumerate(fronts):
        assert front["id"] == i + 1 and front["sector"] == i // 5 + 1
        assert front["missions"] == [i * 10 + 1, i * 10 + 10]
    rows = list(csv.DictReader((SCRIPTS / "VOICE_CUE_MANIFEST.csv").open(
        newline="", encoding="utf-8"
    )))
    assert len(rows) == 231
    assert len({row["cue_id"] for row in rows}) == len(rows)
    assert all(row["recording_status"] == "NOT_RECORDED" for row in rows)
    for path in SCRIPTS.glob("*.md"):
        if path.name not in {
            "VOLUME_01_BORDER_SIGNALS.md", "VOLUME_10_FINAL_OFFENSIVE.md",
            "CHAPTERS_AND_FRONTS.md",
        }:
            continue
        content = path.read_text(encoding="utf-8")
        ids = re.findall(r"\*\*\[(CD-[^·\]\n]+)", content)
        manifest_ids = {row["cue_id"] for row in rows if row["script"] == path.name}
        assert set(i.strip() for i in ids) == manifest_ids
    print("PASS story atlas: 10 regions, 50 fronts, 500 named missions, "
          "250 optional schematic lanes, 7 final 200v200 battles, "
          "231 unrecorded unique voice cues")


if __name__ == "__main__":
    verify()
