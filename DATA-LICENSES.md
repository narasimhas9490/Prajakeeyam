# Data sources and licences

`data/ap_locations.json` is built by `backend/scripts/build_locations.py` from:

| Data | Source | Licence |
|---|---|---|
| 26 districts, 175 assembly constituencies (2024) | [satishvmadala/andhrapradesh_opendata_locations](https://github.com/satishvmadala/andhrapradesh_opendata_locations) (compiled from the AP Gazette and ECI) | GPL-3.0 (repository); facts about administrative units are not copyrightable |
| Constituency → mandal composition, Telugu names | English Wikipedia "… Assembly constituency" and "… mandal" articles via the MediaWiki API | CC BY-SA 4.0 |
| Mandal → village list with LGD codes | [planemad/india-local-government-directory](https://github.com/planemad/india-local-government-directory), a mirror of the Local Government Directory (lgdirectory.gov.in), snapshot 11 March 2022 | Government Open Data License – India |

Villages added after March 2022 come from Wikipedia mandal pages and carry synthetic ids (≥ 9,000,000) recorded in `data/ids/registry.csv`.
