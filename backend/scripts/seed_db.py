#!/usr/bin/env python
"""Upsert data/ap_locations.json into the database (idempotent; safe to re-run after every rebuild).

    DATABASE_URL=postgresql://... python scripts/seed_db.py [path/to/ap_locations.json]
"""
import json
import sys
import time
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND))

from sqlalchemy.dialects.postgresql import insert as pg_insert  # noqa: E402
from sqlalchemy.dialects.sqlite import insert as sqlite_insert  # noqa: E402

from app.db import SessionLocal, engine  # noqa: E402
from app.models import Base, Constituency, District, Mandal, Meta, Village  # noqa: E402


def upsert(db, model, rows: list[dict], batch: int = 2000) -> None:
    if not rows:
        return
    insert = pg_insert if engine.dialect.name == "postgresql" else sqlite_insert
    pk = [c.name for c in model.__table__.primary_key.columns]
    for i in range(0, len(rows), batch):
        chunk = rows[i:i + batch]
        stmt = insert(model.__table__).values(chunk)
        update_cols = {c: getattr(stmt.excluded, c) for c in chunk[0] if c not in pk}
        db.execute(stmt.on_conflict_do_update(index_elements=pk, set_=update_cols))
    db.commit()


def main() -> None:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else BACKEND.parent / "data" / "ap_locations.json"
    bundle = json.loads(path.read_text(encoding="utf-8"))
    started = time.time()
    Base.metadata.create_all(bind=engine)
    with SessionLocal() as db:
        upsert(db, District, [{"id": r[0], "name": r[1], "name_te": r[2]} for r in bundle["districts"]])
        upsert(db, Constituency, [{"id": r[0], "district_id": r[1], "name": r[2], "name_te": r[3], "reservation": r[4]} for r in bundle["constituencies"]])
        upsert(db, Mandal, [{"id": r[0], "constituency_id": r[1], "name": r[2], "name_te": r[3], "kind": r[4]} for r in bundle["mandals"]])
        upsert(db, Village, [{"id": r[0], "mandal_id": r[1], "name": r[2], "name_te": r[3]} for r in bundle["villages"]])
        upsert(db, Meta, [{"key": "locations_version", "value": bundle["meta"]["version"]}])
    print(
        f"seeded {len(bundle['districts'])} districts, {len(bundle['constituencies'])} constituencies, "
        f"{len(bundle['mandals'])} mandals, {len(bundle['villages'])} villages "
        f"(version {bundle['meta']['version']}) into {engine.dialect.name} in {time.time() - started:.1f}s"
    )


if __name__ == "__main__":
    main()
