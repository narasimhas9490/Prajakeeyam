#!/usr/bin/env python
"""Build data/ap_locations.json: Andhra Pradesh districts -> assembly constituencies -> mandals -> villages.

Sources
  * districts + 175 constituencies : github.com/satishvmadala/andhrapradesh_opendata_locations (2024)
  * constituency -> mandals        : English Wikipedia "<Name> Assembly constituency" pages ("Mandals" section)
  * mandal -> villages             : LGD mirror github.com/planemad/india-local-government-directory (Mar 2022)
  * Telugu names                   : Wikipedia language links (te)

Everything downloaded is cached under data/raw so re-runs are offline and fast.
Fix data only through data/overrides/*.csv (never edit the JSON by hand):
  ac_wiki_titles.csv      ac_no,title                 Wikipedia title to use for a constituency
  mandal_aliases.csv      wiki_name,lgd_code,ac_no    force a Wikipedia mandal name onto an LGD sub-district
                                                      (ac_no optional; lgd_code 0 = drop that row)
  ac_mandal_overrides.csv lgd_code,ac_no,action       action = move | add | remove   (applied last)
  names_te.csv            kind,id,name_te             kind = district | constituency | mandal | village

Usage:  python scripts/build_locations.py [--offline] [--skip-telugu]
"""
from __future__ import annotations

import argparse
import csv
import difflib
import gzip
import hashlib
import io
import json
import re
import sys
import time
import unicodedata
import zipfile
from collections import OrderedDict, defaultdict
from datetime import datetime, timezone
from pathlib import Path

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data"
RAW, OVR, REP, IDS = DATA / "raw", DATA / "overrides", DATA / "reports", DATA / "ids"
WIKI_CACHE = RAW / "wiki"
UA = "PrajakeeyamDataBuilder/0.1 (civic app for Andhra Pradesh villages; siva.malapati@purestora.com)"
WIKI_API = "https://en.wikipedia.org/w/api.php"
GH = "https://raw.githubusercontent.com"
SOURCES = {
    "constituencies": (f"{GH}/satishvmadala/andhrapradesh_opendata_locations/main/Final_Andhra_Constituencies_2024.json", RAW / "constituencies_2024.json"),
    "districts": (f"{GH}/satishvmadala/andhrapradesh_opendata_locations/main/AP_Districts_Final.json", RAW / "districts.json"),
    "lgd": (f"{GH}/planemad/india-local-government-directory/main/administrative/4-village.csv.zip", RAW / "lgd_villages.csv.zip"),
}
AP_STATE_CODE = "28"
SYNTH_MANDAL_BASE, SYNTH_VILLAGE_BASE = 900_000, 9_000_000

# The 2022 district reorganisation: which pre-2022 (LGD) districts each new district was carved from.
OLD_DISTRICTS = {
    "Srikakulam": {"SRIKAKULAM"}, "Parvathipuram Manyam": {"VIZIANAGARAM", "SRIKAKULAM"}, "Vizianagaram": {"VIZIANAGARAM"},
    "Visakhapatnam": {"VISAKHAPATANAM"}, "Anakapalli": {"VISAKHAPATANAM"}, "Alluri Sitharama Raju": {"VISAKHAPATANAM", "EAST GODAVARI"},
    "Kakinada": {"EAST GODAVARI"}, "East Godavari": {"EAST GODAVARI", "WEST GODAVARI"}, "Dr. B.R. Ambedkar Konaseema": {"EAST GODAVARI"},
    "West Godavari": {"WEST GODAVARI"}, "Eluru": {"WEST GODAVARI", "KRISHNA"}, "Krishna": {"KRISHNA"}, "NTR": {"KRISHNA"},
    "Guntur": {"GUNTUR"}, "Palnadu": {"GUNTUR"}, "Bapatla": {"GUNTUR", "PRAKASAM"}, "Prakasam": {"PRAKASAM"},
    "Sri Potti Sriramulu Nellore": {"SPSR NELLORE"}, "Tirupati": {"CHITTOOR", "SPSR NELLORE"}, "Chittoor": {"CHITTOOR"},
    "Annamayya": {"Y.S.R.", "CHITTOOR"}, "YSR Kadapa": {"Y.S.R."}, "Kurnool": {"KURNOOL"}, "Nandyal": {"KURNOOL"},
    "Ananthapuramu": {"ANANTAPUR"}, "Sri Sathya Sai": {"ANANTAPUR"},
}

session = requests.Session()
session.headers["User-Agent"] = UA
OFFLINE = False


# ----------------------------------------------------------------------------- helpers
def log(*a):
    print(*a, file=sys.stderr, flush=True)


def fetch_file(key: str) -> Path:
    url, path = SOURCES[key]
    if path.exists():
        return path
    if OFFLINE:
        raise SystemExit(f"--offline but {path} missing")
    log(f"downloading {url}")
    r = session.get(url, timeout=120)
    r.raise_for_status()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(r.content)
    return path


def safe_key(s: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", s)[:150]


def wiki_get(params: dict, cache_key: str) -> dict | None:
    cache = WIKI_CACHE / f"{safe_key(cache_key)}.json"
    if cache.exists():
        return json.loads(cache.read_text(encoding="utf-8"))
    if OFFLINE:
        return None
    r = session.get(WIKI_API, params={**params, "format": "json", "formatversion": 2}, timeout=60)
    r.raise_for_status()
    data = r.json()
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    time.sleep(0.4)
    return data


def read_csv(path: Path) -> list[dict]:
    if not path.exists():
        return []
    with path.open(encoding="utf-8-sig", newline="") as f:
        return [{(k or "").strip(): (v or "").strip() for k, v in row.items()} for row in csv.DictReader(f) if any((row or {}).values())]


def write_csv(path: Path, rows: list[dict], fields: list[str]):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)


DROP_TOKENS = {"mandal", "mandals", "revenue", "m", "r", "u", "part", "pt", "the"}
PHONETIC = [
    (r"sree|shree|shri", "sri"), (r"aa", "a"), (r"ee", "i"), (r"oo", "u"), (r"th", "t"), (r"dh", "d"),
    (r"bh", "b"), (r"sh", "s"), (r"kh", "k"), (r"gh", "g"), (r"ph", "p"), (r"ch", "c"), (r"w", "v"),
    (r"y", "i"), (r"palli$|pally$|pallee$", "palle"), (r"pett$|pet$|pettah$", "peta"), (r"kottah$|kotta$", "kota"),
    (r"(.)\1", r"\1"),  # collapse doubled letters
]


def norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    s = s.lower().replace("&", " and ")
    s = re.sub(r"\((?:part|pt|part of[^)]*)\)", " ", s)
    s = re.sub(r"\bh/?o\.?\s.*$", "", s)  # LGD "X H/O Y" (headquarters) suffixes
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return "".join(t for t in s.split() if t not in DROP_TOKENS)


def pkey(s: str) -> str:
    s = norm(s)
    for pat, rep in PHONETIC:
        s = re.sub(pat, rep, s)
    return s


class Registry:
    """Stable ids for things that have no LGD code (districts, synthetic mandals/villages)."""

    def __init__(self):
        self.path = IDS / "registry.csv"
        self.rows = {(r["kind"], r["key"]): int(r["id"]) for r in read_csv(self.path)}

    def get(self, kind: str, key: str, base: int) -> int:
        k = (kind, key)
        if k not in self.rows:
            used = [v for (kk, _), v in self.rows.items() if kk == kind]
            self.rows[k] = (max(used) + 1) if used else base
        return self.rows[k]

    def save(self):
        write_csv(self.path, [{"kind": k, "key": key, "id": v} for (k, key), v in sorted(self.rows.items(), key=lambda x: x[1])], ["kind", "key", "id"])


# ----------------------------------------------------------------------------- step 1: districts + constituencies
def load_districts_and_acs(reg: Registry):
    districts_raw = json.loads(fetch_file("districts").read_text(encoding="utf-8"))
    acs_raw = json.loads(fetch_file("constituencies").read_text(encoding="utf-8"))
    districts = OrderedDict()
    for d in sorted(districts_raw, key=lambda x: x["district_id"]):
        did = reg.get("district", d["district_id"], 1)
        districts[d["district_id"]] = {"id": did, "name": d["district_name"].strip(), "name_te": None}
    acs = []
    for a in sorted(acs_raw, key=lambda x: float(x["ac_no"])):
        acs.append({
            "id": int(float(a["ac_no"])),
            "district_id": districts[a["district_id"]]["id"],
            "district_name": districts[a["district_id"]]["name"],
            "name": a["ac_name"].strip(),
            "name_te": None,
            "reservation": (a.get("reservation") or "None").strip() or "None",
        })
    assert len(acs) == 175, len(acs)
    return list(districts.values()), acs


# ----------------------------------------------------------------------------- step 2: constituency -> mandals (Wikipedia)
URBAN_RE = re.compile(r"ward|m\.?\s?corp|municipal|corporation|nagar panchayat|\btown\b|\bcity\b|urban local", re.I)
HEAD_RE = re.compile(r"^(mandals?\b|mandals? and|extent|segments?|assembly segments?|areas? covered)", re.I)
JUNK_ROWS = {"part", "pt", "dist", "district", "mandal"}


def wiki_page_html(title: str) -> tuple[str, str] | None:
    d = wiki_get({"action": "parse", "page": title, "prop": "text", "redirects": 1}, f"parse_{title}")
    if not d or "error" in d:
        return None
    return d["parse"]["title"], d["parse"]["text"]


def find_ac_page(ac: dict, title_overrides: dict[int, str], guesses: list[dict]) -> tuple[str, str] | None:
    candidates = []
    if ac["id"] in title_overrides:
        candidates.append(title_overrides[ac["id"]])
    else:
        candidates.append(f"{ac['name']} Assembly constituency")
    for t in candidates:
        res = wiki_page_html(t)
        if res:
            return res
    d = wiki_get({"action": "query", "list": "search", "srsearch": f'"{ac["name"]}" Assembly constituency Andhra Pradesh', "srlimit": 5}, f"search_{ac['name']}")
    for hit in (d or {}).get("query", {}).get("search", []):
        if "assembly constituency" in hit["title"].lower():
            res = wiki_page_html(hit["title"])
            if res:
                guesses.append({"ac_no": ac["id"], "ac_name": ac["name"], "used_title": res[0]})
                return res
    return None


def clean_mandal_line(line: str) -> tuple[str, bool] | None:
    """Return (name, partial) or None if the line is not a mandal (ward lists etc.)."""
    line = re.sub(r"\[\d+\]|\[[a-z]\]", "", line)
    line = re.sub(r",\s*[A-Za-z.\- ]+?\s+district\s*$", "", line, flags=re.I)  # "Panyam , Nandyal district"
    line = line.strip(" .;:,-–")
    if not line:
        return None
    partial = bool(re.search(r"\(\s*(part|pt)\.?\s*\)|\bpart\b", line, re.I))
    line = re.sub(r"\(\s*(part|pt)\.?[^)]*\)", "", line, flags=re.I)
    line = re.sub(r"\(\s*(m\.?\s?corp|municipality|m)\s*\)", "", line, flags=re.I)
    if URBAN_RE.search(line) and not re.search(r"mandal", line, re.I):
        return None
    line = re.sub(r"\bmandals?\b", "", line, flags=re.I)
    line = re.sub(r"\s+", " ", line).strip(" .,-–")
    if not line or len(line) > 40 or re.search(r"\d", line) or line.lower() in JUNK_ROWS:
        return None
    return line, partial


def _link_title(node) -> str | None:
    a = node.find("a", href=True)
    if a and a["href"].startswith("/wiki/") and "redlink" not in a["href"]:
        return a.get("title") or None
    return None


def extract_mandals(html: str) -> list[dict]:
    """[{name, partial, wiki_title}] from the page's Mandals section."""
    soup = BeautifulSoup(html, "lxml")
    out: list[dict] = []
    for h in soup.find_all(re.compile("^h[2-4]$")):
        text = h.get_text(" ", strip=True)
        if not HEAD_RE.search(text):
            continue
        node = h.parent if h.parent and "mw-heading" in (h.parent.get("class") or []) else h
        sib = node.find_next_sibling()
        entries: list[tuple[str, str | None]] = []
        while sib is not None:
            if re.match(r"^h[2-4]$", sib.name or "") or (sib.name == "div" and "mw-heading" in (sib.get("class") or [])):
                break
            if sib.name == "table":
                for tr in sib.find_all("tr"):
                    cells = tr.find_all(["td", "th"])
                    if not cells or cells[0].name == "th":
                        continue
                    first = cells[0]
                    for sup in first.find_all("sup"):
                        sup.decompose()
                    lines = [ln for ln in first.get_text("\n", strip=True).split("\n") if ln.strip()]
                    title = _link_title(first) if len(lines) == 1 else None
                    entries += [(ln, title) for ln in lines]
            elif sib.name in ("ul", "ol"):
                for li in sib.find_all("li"):
                    for sup in li.find_all("sup"):
                        sup.decompose()
                    entries.append((li.get_text(" ", strip=True), _link_title(li)))
            sib = sib.find_next_sibling()
        if not entries:  # inline "The mandals are: A, B and C" style paragraphs
            sib = node.find_next_sibling()
            while sib is not None and sib.name == "p":
                entries += [(a.get_text(" ", strip=True), a.get("title")) for a in sib.find_all("a", href=True) if "mandal" in a["href"].lower()]
                sib = sib.find_next_sibling()
        for raw, title in entries:
            cleaned = clean_mandal_line(raw)
            if cleaned and cleaned[0].lower() not in {m["name"].lower() for m in out}:
                out.append({"name": cleaned[0], "partial": cleaned[1], "wiki_title": title})
        if out:
            break
    return out


# ----------------------------------------------------------------------------- step 3: LGD villages
def load_lgd():
    z = zipfile.ZipFile(fetch_file("lgd"))
    name = next(n for n in z.namelist() if n.endswith(".csv") and not n.startswith("__MACOSX"))
    f = io.TextIOWrapper(z.open(name), encoding="utf-8-sig", newline="")
    r = csv.reader(f)
    header = [h.strip().lower() for h in next(r)]

    def col(*needles):
        for i, h in enumerate(header):
            if all(n in h for n in needles):
                return i
        raise KeyError(needles)

    c_state, c_dist, c_sub_code, c_sub_name = col("state code"), col("district name"), col("subdistrict code"), col("subdistrict name")
    c_v_code, c_v_name, c_v_local, c_status = col("village code"), col("village name", "engl"), col("village name", "local"), col("village status")
    sub_names: dict[int, str] = {}
    sub_dist: dict[int, str] = {}
    villages: dict[int, list[dict]] = defaultdict(list)
    for row in r:
        if row[c_state].strip() != AP_STATE_CODE:
            continue
        if row[c_status].strip().lower().startswith(("un-inhab", "forest")):
            continue
        sid = int(row[c_sub_code])
        sub_names[sid] = row[c_sub_name].strip()
        sub_dist[sid] = row[c_dist].strip().upper()
        local = row[c_v_local].strip()
        vname = re.sub(r"\s+", " ", row[c_v_name].strip())
        villages[sid].append({
            "id": int(row[c_v_code]),
            "name": vname.title() if vname.isupper() else vname,
            "name_te": local if re.search(r"[ఀ-౿]", local) else None,
        })
    return sub_names, sub_dist, villages


# ----------------------------------------------------------------------------- step 4: Telugu names
TE_SUFFIXES = [" శాసనసభ నియోజకవర్గం", " అసెంబ్లీ నియోజకవర్గం", " నియోజకవర్గం", " మండలం", " జిల్లా", " (మండలం)", " (గ్రామం)"]


def telugu_names(titles: list[str]) -> dict[str, str]:
    out: dict[str, str] = {}
    titles = [t for t in dict.fromkeys(titles) if t]
    for i in range(0, len(titles), 50):
        batch = titles[i:i + 50]
        d = wiki_get({"action": "query", "prop": "langlinks", "lllang": "te", "lllimit": 500, "redirects": 1, "titles": "|".join(batch)},
                     "langlinks_" + hashlib.sha1("|".join(batch).encode()).hexdigest()[:16])
        if not d:
            continue
        q = d.get("query", {})
        redirected = {r["to"]: r["from"] for r in q.get("redirects", [])}
        normalised = {r["to"]: r["from"] for r in q.get("normalized", [])}
        for p in q.get("pages", []):
            for ll in p.get("langlinks", []) or []:
                te = re.sub(r"\s*\([^)]*\)\s*$", "", ll.get("title", ""))
                for suf in TE_SUFFIXES:
                    if te.endswith(suf):
                        te = te[: -len(suf)]
                te = te.strip(" ,")
                if not te:
                    continue
                original = p["title"]
                original = redirected.get(original, original)
                original = normalised.get(original, original)
                out[original] = te
                out[p["title"]] = te
    return out


# ----------------------------------------------------------------------------- main
def main():
    global OFFLINE
    ap = argparse.ArgumentParser()
    ap.add_argument("--offline", action="store_true")
    ap.add_argument("--skip-telugu", action="store_true")
    args = ap.parse_args()
    OFFLINE = args.offline
    for d in (RAW, OVR, REP, IDS, WIKI_CACHE):
        d.mkdir(parents=True, exist_ok=True)

    reg = Registry()
    districts, acs = load_districts_and_acs(reg)
    ac_by_no = {ac["id"]: ac for ac in acs}
    ac_old = {ac["id"]: OLD_DISTRICTS.get(ac["district_name"], set()) for ac in acs}
    log(f"districts={len(districts)} constituencies={len(acs)}")

    # --- Wikipedia: constituency -> mandals
    title_overrides = {int(r["ac_no"]): r["title"] for r in read_csv(OVR / "ac_wiki_titles.csv")}
    guesses, no_page, ac_titles = [], [], {}
    wiki_mandals: dict[int, list[dict]] = {}
    for ac in acs:
        res = find_ac_page(ac, title_overrides, guesses)
        if not res:
            no_page.append({"ac_no": ac["id"], "ac_name": ac["name"]})
            wiki_mandals[ac["id"]] = []
            continue
        ac_titles[ac["id"]] = res[0]
        wiki_mandals[ac["id"]] = extract_mandals(res[1])
    log(f"wiki pages found={len(ac_titles)} missing={len(no_page)} guessed={len(guesses)}")

    # --- LGD
    sub_names, sub_dist, lgd_villages = load_lgd()
    log(f"lgd subdistricts={len(sub_names)} villages={sum(len(v) for v in lgd_villages.values())}")
    by_norm: dict[str, list[int]] = defaultdict(list)
    by_pkey: dict[str, list[int]] = defaultdict(list)
    for sid, name in sub_names.items():
        by_norm[norm(name)].append(sid)
        by_pkey[pkey(name)].append(sid)
    pkeys = list(by_pkey)
    aliases: dict[str, int] = {}
    aliases_ac: dict[tuple[int, str], int] = {}
    for r in read_csv(OVR / "mandal_aliases.csv"):
        if r.get("ac_no"):
            aliases_ac[(int(r["ac_no"]), norm(r["wiki_name"]))] = int(r["lgd_code"])
        else:
            aliases[norm(r["wiki_name"])] = int(r["lgd_code"])

    def match_mandal(wiki_name: str, ac_no: int) -> tuple[int | None, list[int]]:
        """Return (lgd_code | 0 for drop | None for unmatched, candidate codes for the report)."""
        n, p = norm(wiki_name), pkey(wiki_name)
        if (ac_no, n) in aliases_ac:
            return aliases_ac[(ac_no, n)], []
        if n in aliases:
            return aliases[n], []
        # Tiers: exact spelling -> phonetic key -> fuzzy. Within a tier prefer LGD sub-districts whose
        # pre-2022 district is compatible with the constituency's district (AP reuses mandal names a lot).
        fuzzy: set[int] = set()
        for close in difflib.get_close_matches(p, pkeys, n=6, cutoff=0.85):
            fuzzy |= set(by_pkey[close])
        last_pool: list[int] = []
        for cands in (set(by_norm.get(n, [])), set(by_pkey.get(p, [])), fuzzy):
            if not cands:
                continue
            compatible = {c for c in cands if sub_dist.get(c) in ac_old[ac_no]} if ac_old[ac_no] else set()
            pool = compatible or cands
            if len(pool) == 1:
                return next(iter(pool)), []
            if compatible:  # several plausible mandals in the right district: a human must decide
                return None, sorted(pool)
            last_pool = sorted(pool)
        if not last_pool:
            weak = difflib.get_close_matches(p, pkeys, n=3, cutoff=0.6)
            last_pool = [c for w in weak for c in by_pkey[w]]
        return None, last_pool

    # --- match wiki mandals to LGD sub-districts
    claims: dict[int, list[tuple[int, str, bool, str | None]]] = defaultdict(list)
    unmatched: list[dict] = []
    synthetic_from_wiki: list[tuple[int, str, str | None]] = []
    for ac in acs:
        for m in wiki_mandals[ac["id"]]:
            code, cand = match_mandal(m["name"], ac["id"])
            if code == 0:
                continue
            if code is None:
                unmatched.append({"ac_no": ac["id"], "ac_name": ac["name"], "wiki_name": m["name"],
                                  "candidates": "; ".join(f"{sub_names[c]} [{sub_dist[c]}]={c}" for c in cand)})
                synthetic_from_wiki.append((ac["id"], m["name"], m["wiki_title"]))
                continue
            claims[code].append((ac["id"], m["name"], m["partial"], m["wiki_title"]))

    # --- resolve claims: non-partial first, then lowest ac_no
    mandal_ac: dict[int, int] = {}
    mandal_name: dict[int, str] = {}
    mandal_title: dict[int, str | None] = {}
    splits = []
    for code, lst in claims.items():
        lst_sorted = sorted(lst, key=lambda x: (x[2], x[0]))
        mandal_ac[code] = lst_sorted[0][0]
        mandal_name[code] = lst_sorted[0][1]
        mandal_title[code] = next((t for _, _, _, t in lst_sorted if t), None)
        if len({a for a, _, _, _ in lst}) > 1:
            splits.append({"lgd_code": code, "lgd_name": sub_names[code], "assigned_ac": lst_sorted[0][0],
                           "all_claims": "; ".join(f"{a}{'(part)' if p else ''}" for a, _, p, _ in lst_sorted)})

    # --- orphans: LGD sub-districts nobody claimed -> look at the "<Name> mandal" Wikipedia page for its constituency
    ac_by_norm_name: dict[str, int] = {}
    for ac in acs:
        key = norm(ac["name"])
        ac_by_norm_name[key] = -1 if key in ac_by_norm_name else ac["id"]  # -1 marks ambiguous names
    orphans = []
    for sid, name in sub_names.items():
        if sid in mandal_ac:
            continue
        resolved = None
        res = wiki_page_html(f"{name} mandal")
        if res:
            soup = BeautifulSoup(res[1], "lxml")
            for a in soup.select("a[href*='Assembly_constituency']"):
                t = a.get("title") or a["href"].split("/wiki/")[-1].replace("_", " ")
                key = norm(re.sub(r"assembly constituency", "", t, flags=re.I).replace("(", " ").replace(")", " ").split(",")[0])
                hit = ac_by_norm_name.get(key, None)
                if hit and hit > 0 and sub_dist.get(sid) in (ac_old[hit] or {sub_dist.get(sid)}):
                    resolved = hit
                    break
        if resolved:
            mandal_ac[sid] = resolved
            mandal_name[sid] = name
            mandal_title[sid] = res[0] if res else None
        orphans.append({"lgd_code": sid, "lgd_name": name, "lgd_district": sub_dist[sid], "resolved_ac": resolved or ""})

    # --- overrides (last word)
    for r in read_csv(OVR / "ac_mandal_overrides.csv"):
        code, ac_no, action = int(r["lgd_code"]), int(r["ac_no"]), r["action"].lower()
        if action in ("move", "add"):
            mandal_ac[code] = ac_no
            mandal_name.setdefault(code, sub_names.get(code, str(code)))
            mandal_title.setdefault(code, None)
        elif action == "remove":
            mandal_ac.pop(code, None)

    # --- assemble mandals + villages
    mandals: list[dict] = []
    villages: list[dict] = []
    for code, ac_no in sorted(mandal_ac.items(), key=lambda x: (x[1], mandal_name[x[0]])):
        mandals.append({"id": code, "constituency_id": ac_no, "name": mandal_name[code], "name_te": None, "kind": "rural", "wiki_title": mandal_title.get(code)})
        villages += [{**v, "mandal_id": code} for v in sorted(lgd_villages.get(code, []), key=lambda v: v["name"])]
    # wiki mandals with no LGD counterpart: synthetic mandal, villages from the "<Name> mandal" page if it has a list
    fallback_rows = []
    for ac_no, wname, wtitle in synthetic_from_wiki:
        mid = reg.get("mandal", f"{ac_no}:{wname}", SYNTH_MANDAL_BASE)
        mandals.append({"id": mid, "constituency_id": ac_no, "name": wname, "name_te": None, "kind": "rural", "wiki_title": wtitle})
        res = wiki_page_html(wtitle or f"{wname} mandal")
        count = 0
        if res:
            soup = BeautifulSoup(res[1], "lxml")
            for h in soup.find_all(re.compile("^h[2-4]$")):
                if not re.search(r"villages", h.get_text(" ", strip=True), re.I):
                    continue
                node = h.parent if h.parent and "mw-heading" in (h.parent.get("class") or []) else h
                sib = node.find_next_sibling()
                while sib is not None and not (re.match(r"^h[2-4]$", sib.name or "") or (sib.name == "div" and "mw-heading" in (sib.get("class") or []))):
                    for li in sib.find_all("li") if sib.name in ("ul", "ol", "div") else []:
                        vname = re.sub(r"\[\d+\]", "", li.get_text(" ", strip=True)).strip()
                        if vname and len(vname) < 60 and not re.search(r"\d", vname):
                            villages.append({"id": reg.get("village", f"{mid}:{vname}", SYNTH_VILLAGE_BASE), "mandal_id": mid, "name": vname, "name_te": None})
                            count += 1
                    sib = sib.find_next_sibling()
                break
        fallback_rows.append({"ac_no": ac_no, "wiki_name": wname, "synthetic_mandal_id": mid, "villages_from_wiki": count})
    # constituencies with no mandal at all (fully urban): synthetic mandal so posting still works
    acs_without = [ac for ac in acs if not any(m["constituency_id"] == ac["id"] for m in mandals)]
    for ac in acs_without:
        mid = reg.get("mandal", f"{ac['id']}:urban", SYNTH_MANDAL_BASE)
        mandals.append({"id": mid, "constituency_id": ac["id"], "name": f"{ac['name']} (Town)", "name_te": None, "kind": "urban", "wiki_title": None})

    # --- Telugu names
    te_overrides = {(r["kind"], int(r["id"])): r["name_te"] for r in read_csv(OVR / "names_te.csv")}
    if not args.skip_telugu:
        mandal_titles = {m["id"]: (m["wiki_title"] or f"{m['name']} mandal") for m in mandals if m["kind"] == "rural"}
        te = telugu_names([f"{d['name']} district" for d in districts] + list(ac_titles.values()) + list(mandal_titles.values()))
        for d in districts:
            d["name_te"] = te.get(f"{d['name']} district")
        for ac in acs:
            ac["name_te"] = te.get(ac_titles.get(ac["id"], ""))
        for m in mandals:
            m["name_te"] = te.get(mandal_titles.get(m["id"], ""))
    for coll, kind in ((districts, "district"), (acs, "constituency"), (mandals, "mandal"), (villages, "village")):
        for item in coll:
            if (kind, item["id"]) in te_overrides:
                item["name_te"] = te_overrides[(kind, item["id"])]

    # --- write bundle
    payload = {
        "districts": [[d["id"], d["name"], d["name_te"]] for d in districts],
        "constituencies": [[a["id"], a["district_id"], a["name"], a["name_te"], a["reservation"]] for a in acs],
        "mandals": [[m["id"], m["constituency_id"], m["name"], m["name_te"], m["kind"]] for m in mandals],
        "villages": [[v["id"], v["mandal_id"], v["name"], v["name_te"]] for v in villages],
    }
    digest = hashlib.sha1(json.dumps(payload, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:10]
    out_path = DATA / "ap_locations.json"
    previous = json.loads(out_path.read_text(encoding="utf-8")) if out_path.exists() else {}
    version = previous.get("meta", {}).get("version") if previous.get("meta", {}).get("digest") == digest else f"{datetime.now(timezone.utc):%Y%m%d}-{digest}"
    bundle = {"meta": {"version": version, "digest": digest, "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds")}, **payload}
    text = json.dumps(bundle, ensure_ascii=False, separators=(",", ":"))
    out_path.write_text(text, encoding="utf-8")
    with gzip.open(DATA / "ap_locations.json.gz", "wb", compresslevel=9) as gz:
        gz.write(text.encode("utf-8"))
    reg.save()

    # --- reports
    write_csv(REP / "unmatched_mandals.csv", unmatched, ["ac_no", "ac_name", "wiki_name", "candidates"])
    write_csv(REP / "split_mandals.csv", splits, ["lgd_code", "lgd_name", "assigned_ac", "all_claims"])
    write_csv(REP / "orphan_lgd_mandals.csv", orphans, ["lgd_code", "lgd_name", "lgd_district", "resolved_ac"])
    write_csv(REP / "wiki_title_guesses.csv", guesses + [{"ac_no": n["ac_no"], "ac_name": n["ac_name"], "used_title": "<NO PAGE>"} for n in no_page], ["ac_no", "ac_name", "used_title"])
    write_csv(REP / "wiki_fallback_mandals.csv", fallback_rows, ["ac_no", "wiki_name", "synthetic_mandal_id", "villages_from_wiki"])
    per_ac = defaultdict(int)
    for m in mandals:
        per_ac[m["constituency_id"]] += 1
    per_mandal = defaultdict(int)
    for v in villages:
        per_mandal[v["mandal_id"]] += 1
    write_csv(REP / "mandals_per_constituency.csv",
              [{"ac_no": ac["id"], "ac_name": ac["name"], "district": ac["district_name"], "wiki_title": ac_titles.get(ac["id"], ""), "mandals": per_ac[ac["id"]],
                "mandal_names": "; ".join(m["name"] for m in mandals if m["constituency_id"] == ac["id"])} for ac in acs],
              ["ac_no", "ac_name", "district", "wiki_title", "mandals", "mandal_names"])
    unresolved_orphans = [o for o in orphans if not o["resolved_ac"]]
    report = [
        f"# Build report ({bundle['meta']['built_at']}) version {version}", "",
        f"- districts: {len(districts)}", f"- constituencies: {len(acs)}", f"- mandals: {len(mandals)} (synthetic urban: {len(acs_without)}, wiki-only: {len(synthetic_from_wiki)})",
        f"- villages: {len(villages)}", f"- Telugu names: districts {sum(bool(d['name_te']) for d in districts)}, constituencies {sum(bool(a['name_te']) for a in acs)}, mandals {sum(bool(m['name_te']) for m in mandals)}, villages {sum(bool(v['name_te']) for v in villages)}",
        f"- wiki pages missing: {len(no_page)}, guessed titles: {len(guesses)}", f"- unmatched wiki mandals: {len(unmatched)}", f"- split mandals: {len(splits)}",
        f"- orphan LGD sub-districts: {len(orphans)} (auto-resolved via mandal page: {len(orphans) - len(unresolved_orphans)}, still unresolved: {len(unresolved_orphans)})", "",
        "## Constituencies with fewest mandals", *[f"- {ac['id']} {ac['name']} ({ac['district_name']}): {per_ac[ac['id']]}" for ac in sorted(acs, key=lambda a: per_ac[a['id']])[:15]], "",
        "## Unresolved orphan LGD sub-districts", *[f"- {o['lgd_code']} {o['lgd_name']} [{o['lgd_district']}]" for o in unresolved_orphans], "",
        "## Rural mandals with no villages", *[f"- {m['id']} {m['name']} (AC {m['constituency_id']})" for m in mandals if m["kind"] == "rural" and per_mandal[m["id"]] == 0][:60],
    ]
    (REP / "build_report.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    log("\n".join(report[:12]))
    log(f"wrote {out_path} ({len(text) / 1e6:.2f} MB) and .gz ({(DATA / 'ap_locations.json.gz').stat().st_size / 1e3:.0f} KB)")


if __name__ == "__main__":
    main()
