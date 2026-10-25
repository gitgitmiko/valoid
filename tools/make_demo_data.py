#!/usr/bin/env python3
"""Generates the Demo Mode responses in app/src/main/assets/demo/ (Python standard library only).

    python3 tools/make_demo_data.py

Responses mimic the Riot endpoints that RiotRepository reads, using real IDs from valorant-api.com so
names and images resolve. Times are placeholders that DemoInterceptor fills in when serving:
  "{{ms:N}}"     epoch millis, now + N seconds (number)
  "{{iso:N}}"    ISO-8601 instant, now + N seconds (string)
  "{{left:N}}"   seconds until the next 00:00 UTC, plus N days (number)
  "{{isoday:N}}" ISO-8601 of the next 00:00 UTC, plus N days (string)
Contracts and ranks follow the act running when this script runs; regenerate before each release.
"""
import datetime
import json
import os
import random
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "demo")
PUUID = "6f1d2c4a-8b3e-4d5f-9a7c-0d3e0a1d0001"
rng = random.Random(26)

TIER_PRICE = {
    "12683d76-48d7-84a3-4e09-6985794f0445": 875,   # Select
    "0cebb8be-46d7-c12a-d306-e9907bfc5a25": 1275,  # Deluxe
    "60bca009-4182-7998-dee7-b8a2558dc369": 1775,  # Premium
    "e046854e-406c-37f4-6607-19a9ba8426fc": 2175,  # Exclusive
    "411e4a55-4e59-7757-41f0-86a53f101bb5": 2475,  # Ultra
}
TYPE_SKIN = "e7c63390-eda7-46e0-bb7a-a6abdacd2433"
TYPE_CARD = "3f296c07-64c3-494c-923b-fe692a4fa1bd"
TYPE_SPRAY = "d5f120f8-ff8c-4aac-92ea-f2b5acbe9475"
TYPE_TITLE = "de7caa6b-adf7-4588-bbd1-143831e786c6"
TYPE_BUDDY = "dd3bf334-87f3-40bd-b043-682a57a8dc3a"
VP = "85ad13f7-3d1b-5128-9eb2-7cd8ee0b5741"
KC = "85ca954a-41f2-ce94-9b45-8ca3dd39a00d"
RAD = "e59aa87c-4cbf-517a-5983-6e81511be9b7"


def api(path):
    with urllib.request.urlopen(f"https://valorant-api.com/v1/{path}") as response:
        return json.load(response)["data"]


def write(name, data):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, separators=(",", ":"))


def uid():
    return "%08x-%04x-4%03x-a%03x-%012x" % (rng.getrandbits(32), rng.getrandbits(16), rng.getrandbits(12),
                                             rng.getrandbits(12), rng.getrandbits(48))


def main():
    os.makedirs(OUT, exist_ok=True)
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    weapons = api("weapons")
    skins = []  # (skin, weapon, first level uuid, price)
    for w in weapons:
        for s in w["skins"]:
            price = TIER_PRICE.get(s.get("contentTierUuid"))
            if price and s["levels"] and not s["displayName"].startswith("Standard"):
                skins.append((s, w, s["levels"][0]["uuid"], price))
    rng.shuffle(skins)

    # Store: 4 daily offers, 6 Night Market offers, 2 bundles, 4 accessories.
    daily = skins[:4]
    night = skins[4:10]
    store = {
        "SkinsPanelLayout": {
            "SingleItemOffers": [lvl for _, _, lvl, _ in daily],
            "SingleItemStoreOffers": [{"OfferID": lvl, "Cost": {VP: price}} for _, _, lvl, price in daily],
            "SingleItemOffersRemainingDurationInSeconds": "{{left:0}}",
        },
        "BonusStore": {
            "BonusStoreOffers": [
                {"Offer": {"OfferID": lvl, "Cost": {VP: price}}, "DiscountPercent": pct,
                 "DiscountCosts": {VP: price * (100 - pct) // 100}}
                for (_, _, lvl, price), pct in zip(night, [17, 24, 31, 38, 42, 49])
            ],
            "BonusStoreRemainingDurationInSeconds": "{{left:9}}",
        },
    }
    bundles = []
    for bundle in api("bundles"):
        base = bundle["displayName"].split()[0]
        members = [x for x in skins if x[0]["displayName"].startswith(bundle["displayName"] + " ")]
        if len(members) >= 4 and bundle.get("displayIcon") and len(bundles) < 2 and base not in [b[2] for b in bundles]:
            bundles.append((bundle, members[:5], base))
    store["FeaturedBundle"] = {
        "Bundles": [{
            "ID": uid(),
            "DataAssetID": bundle["uuid"],
            "Items": [{"Item": {"ItemTypeID": TYPE_SKIN, "ItemID": lvl, "Amount": 1},
                       "BasePrice": price, "DiscountedPrice": price * 85 // 100}
                      for _, _, lvl, price in members],
            "TotalBaseCost": {VP: sum(p for *_, p in members)},
            "TotalDiscountedCost": {VP: sum(p * 85 // 100 for *_, p in members)},
            "DurationRemainingInSeconds": f"{{{{left:{days}}}}}",
        } for (bundle, members, _), days in zip(bundles, [6, 13])],
        "BundleRemainingDurationInSeconds": "{{left:6}}",
    }
    buddies = [b for b in api("buddies") if b["levels"]]
    sprays = [s for s in api("sprays") if s.get("fullTransparentIcon")]
    cards = [c for c in api("playercards") if c.get("wideArt") and c.get("largeArt")]
    titles = [t for t in api("playertitles") if t.get("titleText") and not any(w in t["titleText"].upper() for w in ("PREMIER", "CHAMPION", "VCT", "//"))]
    rng.shuffle(buddies), rng.shuffle(sprays), rng.shuffle(cards), rng.shuffle(titles)
    store["AccessoryStore"] = {
        "AccessoryStoreOffers": [
            {"Offer": {"OfferID": uid(), "Cost": {KC: cost}, "Rewards": [{"ItemTypeID": t, "ItemID": i, "Quantity": 1}]}}
            for t, i, cost in [
                (TYPE_BUDDY, buddies[0]["levels"][0]["uuid"], 3000),
                (TYPE_SPRAY, sprays[0]["uuid"], 2500),
                (TYPE_CARD, cards[0]["uuid"], 4000),
                (TYPE_TITLE, titles[0]["uuid"], 2000),
            ]
        ],
        "AccessoryStoreRemainingDurationInSeconds": "{{left:3}}",
    }
    write("storefront.json", store)
    write("wallet.json", {"Balances": {VP: 4350, RAD: 120, KC: 8250}})

    # Loadout and collection.
    gun_ids = {w["uuid"]: w for w in weapons}
    owned_skins = skins[10:40]
    by_weapon = {}
    for s, w, lvl, _ in owned_skins:
        by_weapon.setdefault(w["uuid"], s)
    guns = []
    for wid, w in gun_ids.items():
        skin = by_weapon.get(wid) or next(s for s in w["skins"] if s["displayName"].startswith("Standard") or s["uuid"] == w["defaultSkinUuid"])
        guns.append({
            "ID": wid, "SkinID": skin["uuid"], "SkinLevelID": skin["levels"][0]["uuid"],
            "ChromaID": skin["chromas"][0]["uuid"] if skin["chromas"] else None,
            **({"CharmLevelID": buddies[len(guns) % 6 + 1]["levels"][0]["uuid"]} if wid in by_weapon and len(guns) % 3 == 0 else {}),
        })
    write("loadout.json", {
        "Subject": PUUID,
        "Guns": guns,
        "Identity": {"PlayerCardID": cards[1]["uuid"], "PlayerTitleID": titles[1]["uuid"], "AccountLevel": 147},
        "Expressions": {"AESSelections": [{"AssetID": s["uuid"]} for s in sprays[1:5]]},
    })
    owned = {
        TYPE_SKIN: [lvl for _, _, lvl, _ in owned_skins],
        TYPE_CARD: [c["uuid"] for c in cards[1:13]],
        TYPE_SPRAY: [s["uuid"] for s in sprays[1:16]],
        TYPE_TITLE: [t["uuid"] for t in titles[1:11]],
        TYPE_BUDDY: [b["levels"][0]["uuid"] for b in buddies[1:10]],
    }
    for type_id, ids in owned.items():
        write(f"entitlements-{type_id}.json", {"ItemTypeID": type_id, "Entitlements": [{"TypeID": type_id, "ItemID": i} for i in ids]})

    # Ranks and career.
    seasons = api("seasons")
    acts = sorted([s for s in seasons if s["type"] == "EAresSeasonType::Act" and s["startTime"] <= now], key=lambda s: s["startTime"])
    current = acts[-1]
    competitive = {}
    for act, tier in zip(acts[-4:], [16, 17, 18, 18]):
        games = rng.randint(24, 70)
        wins = games // 2 + rng.randint(1, 6)
        competitive[act["uuid"]] = {
            "SeasonID": act["uuid"], "NumberOfWins": wins, "NumberOfWinsWithPlacements": wins, "NumberOfGames": games,
            "Rank": tier, "CompetitiveTier": tier, "RankedRating": rng.randint(10, 90),
            "WinsByTier": {str(tier - 1): wins // 2, str(tier): wins - wins // 2, **({"19": 1} if act is acts[-2] else {})},
        }
    queues = {"competitive": {"SeasonalInfoBySeasonID": competitive}}
    for queue, games in [("unrated", 31), ("swiftplay", 18), ("deathmatch", 22), ("spikerush", 9)]:
        queues[queue] = {"SeasonalInfoBySeasonID": {current["uuid"]: {
            "SeasonID": current["uuid"], "NumberOfGames": games, "NumberOfWins": games // 2 + 2,
            "NumberOfWinsWithPlacements": games // 2 + 2, "CompetitiveTier": 0, "RankedRating": 0}}}
    write("mmr.json", {
        "Subject": PUUID,
        "QueueSkills": queues,
        "LatestCompetitiveUpdate": {"MatchID": "", "SeasonID": current["uuid"], "TierAfterUpdate": 18, "RankedRatingAfterUpdate": 64,
                                    "RankedRatingEarned": 21},
    })

    # Matches.
    maps = [m for m in api("maps") if m.get("tacticalDescription") and m.get("listViewIcon")]
    agents = api("agents?isPlayableCharacter=true")
    names = ["Nyx", "Kopi Susu", "Tetris", "Bayu", "Rara", "Momo", "Senja", "Pixel", "Gecko", "Arunika", "Kilat", "Lumen",
             "Rusa", "Bintang", "Salju", "Taro", "Ombak", "Mochi", "Vega", "Rimba"]
    tags = ["ID1", "SEA", "0610", "777", "JKT", "BDG", "SBY", "2026", "VLR", "GG"]
    roster = {PUUID: ("ValoidDemo", "PLAY")}
    matches, rr_updates, xp_history = [], [], []
    plan = [("competitive", True, 13, 9, 21), ("competitive", False, 10, 13, -17), ("unrated", True, 13, 7, None),
            ("competitive", True, 14, 12, 18), ("swiftplay", False, 3, 5, None), ("competitive", True, 13, 11, 19),
            ("deathmatch", None, 0, 0, None)]
    for index, (queue, i_win, my_rounds, enemy_rounds, rr) in enumerate(plan):
        match_id = uid()
        started = -(index * 7 + 2) * 3600
        game_map = maps[index % len(maps)]
        players = []
        picked = rng.sample(agents, 10)
        for slot in range(10):
            pid = PUUID if slot == 0 else uid()
            if pid not in roster:
                roster[pid] = (rng.choice(names) + ("" if rng.random() < .6 else str(rng.randint(2, 99))), rng.choice(tags))
            team = ("Blue" if slot < 5 else "Red") if queue != "deathmatch" else pid
            players.append({"subject": pid, "teamId": team, "characterId": picked[slot]["uuid"],
                            "gameName": roster[pid][0], "tagLine": roster[pid][1],
                            "competitiveTier": (18 if pid == PUUID else rng.randint(15, 20)) if queue == "competitive" else 0,
                            "accountLevel": rng.randint(20, 400) if pid != PUUID else 147})
        blue, red = players[:5], players[5:]
        rounds, totals = [], {p["subject"]: {"kills": 0, "deaths": 0, "assists": 0, "score": 0} for p in players}
        if queue == "deathmatch":
            for p in players:
                k = rng.randint(14, 32) if p["subject"] != PUUID else 34
                totals[p["subject"]].update(kills=k, deaths=rng.randint(14, 30), assists=rng.randint(0, 6), score=k * 100)
            round_count = 18
        else:
            winners = (["Blue"] * my_rounds + ["Red"] * enemy_rounds)
            rng.shuffle(winners)
            round_count = len(winners)
            for number, winner in enumerate(winners):
                alive = {p["subject"]: p for p in players}
                stats = {p["subject"]: {"subject": p["subject"], "kills": [], "damage": [],
                                        "economy": {"spent": rng.choice([800, 2900, 3900, 4700]),
                                                    "loadoutValue": rng.choice([1600, 3700, 4700, 5100])}} for p in players}
                time = 0
                win_team, lose_team = (blue, red) if winner == "Blue" else (red, blue)
                victims = rng.sample(lose_team, 5)
                extra = rng.sample(win_team, rng.randint(0, 3))
                order = [(rng.choice([w for w in win_team if w["subject"] in alive] or win_team), v) for v in victims]
                order += [(rng.choice(lose_team), e) for e in extra]
                rng.shuffle(order)
                for killer, victim in order:
                    if killer["subject"] not in alive or victim["subject"] not in alive or killer["teamId"] == victim["teamId"]:
                        continue
                    if killer["subject"] == PUUID or (killer["teamId"] == "Blue" and rng.random() < .12):
                        killer = next(p for p in players if p["subject"] == PUUID) if PUUID in alive else killer
                    time += rng.randint(4000, 18000)
                    del alive[victim["subject"]]
                    stats[killer["subject"]]["kills"].append({"killer": killer["subject"], "victim": victim["subject"], "roundTime": time})
                    head = rng.choice([0, 0, 1, 1, 2])
                    stats[killer["subject"]]["damage"].append({"receiver": victim["subject"], "damage": 150,
                                                               "headshots": head, "bodyshots": rng.randint(1, 3), "legshots": rng.choice([0, 0, 1])})
                    totals[killer["subject"]]["kills"] += 1
                    totals[killer["subject"]]["score"] += 200 + rng.randint(0, 70)
                    totals[victim["subject"]]["deaths"] += 1
                    helper = rng.choice([p for p in players if p["teamId"] == killer["teamId"] and p is not killer])
                    if rng.random() < .4:
                        totals[helper["subject"]]["assists"] += 1
                        totals[helper["subject"]]["score"] += 50
                planted = rng.random() < .55
                attackers = blue if number < 12 else red
                code = rng.choice(["Elimination", "Elimination", "Defuse", "Detonate"]) if planted else "Elimination"
                rounds.append({
                    "roundNum": number, "winningTeam": winner, "roundResultCode": code,
                    "roundResult": {"Elimination": "Eliminated", "Defuse": "Bomb defused", "Detonate": "Bomb detonated"}[code],
                    **({"bombPlanter": rng.choice(attackers)["subject"]} if planted else {}),
                    **({"bombDefuser": rng.choice(blue if attackers is red else red)["subject"]} if code == "Defuse" else {}),
                    "playerStats": list(stats.values()),
                })
        for p in players:
            t = totals[p["subject"]]
            p["stats"] = {"score": t["score"] + t["kills"] * 30, "roundsPlayed": round_count, "kills": t["kills"],
                          "deaths": t["deaths"], "assists": t["assists"]}
        teams = [] if queue == "deathmatch" else [
            {"teamId": "Blue", "won": my_rounds > enemy_rounds, "roundsWon": my_rounds, "roundsPlayed": round_count},
            {"teamId": "Red", "won": enemy_rounds > my_rounds, "roundsWon": enemy_rounds, "roundsPlayed": round_count},
        ]
        write(f"match-{match_id}.json", {
            "matchInfo": {"matchId": match_id, "mapId": game_map["mapUrl"], "queueID": queue, "provisioningFlowID": "Matchmaking",
                          "gameStartMillis": f"{{{{ms:{started}}}}}", "gameLengthMillis": (round_count * 95 + 120) * 1000,
                          "seasonId": current["uuid"], "isRanked": queue == "competitive"},
            "players": players, "teams": teams, "roundResults": rounds,
        })
        matches.append({"MatchID": match_id, "GameStartTime": f"{{{{ms:{started}}}}}", "QueueID": queue})
        if rr is not None:
            rr_updates.append({"MatchID": match_id, "MapID": game_map["mapUrl"], "SeasonID": current["uuid"],
                               "MatchStartTime": f"{{{{ms:{started}}}}}", "TierAfterUpdate": 18, "RankedRatingEarned": rr})
        xp = 4000 + rng.randint(0, 2000) + (4000 if index == 0 else 0)
        xp_history.append({"ID": match_id, "MatchStart": f"{{{{iso:{started}}}}}", "XPDelta": xp,
                           "XPSources": [{"ID": "time-played", "Amount": xp - (4000 if index == 0 else 0)},
                                         *([{"ID": "first-win-of-the-day", "Amount": 4000}] if index == 0 else [])]})
    write("match-history.json", {"Subject": PUUID, "History": matches})
    write("competitiveupdates.json", {"Subject": PUUID, "Matches": rr_updates})
    write("names.json", {pid: [name, tag] for pid, (name, tag) in roster.items()})
    write("account-xp.json", {"Subject": PUUID, "Progress": {"Level": 147, "XP": 3120}, "History": xp_history,
                              "NextTimeFirstWinAvailable": "{{iso:61200}}"})

    # Contracts and missions of the current act.
    contracts = api("contracts")
    battle_pass = next(c for c in contracts if c["content"]["relationType"] == "Season" and c["content"]["relationUuid"] == current["uuid"])
    agent_contracts = [c for c in contracts if c["content"]["relationType"] == "Agent"]
    rng.shuffle(agent_contracts)
    missions = api("missions")
    dailies = [m for m in missions if m["type"] == "EAresMissionType::Daily"][:4]
    weeklies = [m for m in missions if m["type"] == "EAresMissionType::Weekly" and (m.get("activationDate") or "9") <= now < (m.get("expirationDate") or "0")][:3]

    def mission(m, progress, expires):
        target = m.get("progressToComplete") or 1
        objective = m["objectives"][0]["objectiveUuid"] if m.get("objectives") else uid()
        value = min(progress, target)
        return {"ID": m["uuid"], "Objectives": {objective: value}, "Complete": value >= target, "ExpirationTime": expires}
    write("contracts.json", {
        "Subject": PUUID,
        "Contracts": [
            {"ContractDefinitionID": battle_pass["uuid"], "ProgressionLevelReached": 23, "ProgressionTowardsNextLevel": 9250},
            {"ContractDefinitionID": agent_contracts[0]["uuid"], "ProgressionLevelReached": 6, "ProgressionTowardsNextLevel": 21000},
            {"ContractDefinitionID": agent_contracts[1]["uuid"], "ProgressionLevelReached": 2, "ProgressionTowardsNextLevel": 9500},
        ],
        "ActiveSpecialContract": agent_contracts[0]["uuid"],
        "Missions": [mission(m, p, "{{isoday:0}}") for m, p in zip(dailies, [1, 640, 0, 1])]
        + [mission(m, p, "{{isoday:5}}") for m, p in zip(weeklies, [37, 4, 2])],
        "MissionMetadata": {"NPECompleted": True, "WeeklyCheckpoint": "{{iso:-172800}}", "WeeklyRefillTime": "{{isoday:5}}"},
    })
    print(f"DEMO_DATA_OK {len(os.listdir(OUT))} files -> {OUT} (act {current['title']})")


if __name__ == "__main__":
    main()
