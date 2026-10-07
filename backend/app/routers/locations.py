"""Location hierarchy: one cached gzipped bundle built from the DB, plus a village search."""
import gzip
import json
from threading import Lock

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Constituency, District, Mandal, Meta, Village
from ..schemas import VillageHit

router = APIRouter(tags=["locations"])

_cache: dict = {"version": None, "raw": b"", "gz": b""}
_lock = Lock()


def refresh_bundle(db: Session) -> str | None:
    """(Re)build the in-memory bundle from the DB. Returns the version or None if not seeded."""
    version = db.get(Meta, "locations_version")
    if version is None:
        return None
    payload = {
        "meta": {"version": version.value},
        "districts": [[r.id, r.name, r.name_te] for r in db.execute(select(District.id, District.name, District.name_te).order_by(District.id))],
        "constituencies": [
            [r.id, r.district_id, r.name, r.name_te, r.reservation]
            for r in db.execute(select(Constituency.id, Constituency.district_id, Constituency.name, Constituency.name_te, Constituency.reservation).order_by(Constituency.id))
        ],
        "mandals": [
            [r.id, r.constituency_id, r.name, r.name_te, r.kind]
            for r in db.execute(select(Mandal.id, Mandal.constituency_id, Mandal.name, Mandal.name_te, Mandal.kind).order_by(Mandal.constituency_id, Mandal.name))
        ],
        "villages": [
            [r.id, r.mandal_id, r.name, r.name_te]
            for r in db.execute(select(Village.id, Village.mandal_id, Village.name, Village.name_te).order_by(Village.mandal_id, Village.name))
        ],
    }
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with _lock:
        _cache.update(version=version.value, raw=raw, gz=gzip.compress(raw, 9))
    return version.value


def _ensure_loaded(db: Session) -> None:
    if _cache["version"] is None:
        refresh_bundle(db)
    if _cache["version"] is None:
        raise HTTPException(status_code=503, detail="Locations not seeded yet")


@router.get("/locations/version")
def locations_version(db: Session = Depends(get_db)):
    _ensure_loaded(db)
    return {"version": _cache["version"]}


@router.get("/locations/bundle")
def locations_bundle(request: Request, db: Session = Depends(get_db)):
    _ensure_loaded(db)
    etag = f'"{_cache["version"]}"'
    headers = {"ETag": etag, "Cache-Control": "public, max-age=86400"}
    if request.headers.get("if-none-match") == etag:
        return Response(status_code=304, headers=headers)
    if "gzip" in request.headers.get("accept-encoding", ""):
        return Response(_cache["gz"], media_type="application/json", headers={**headers, "Content-Encoding": "gzip", "Vary": "Accept-Encoding"})
    return Response(_cache["raw"], media_type="application/json", headers=headers)


@router.get("/search/villages", response_model=list[VillageHit])
def search_villages(q: str = Query(min_length=2, max_length=60), limit: int = Query(20, ge=1, le=50), db: Session = Depends(get_db)):
    pattern = q.strip().lower() + "%"
    rows = db.execute(
        select(Village, Mandal, Constituency)
        .join(Mandal, Village.mandal_id == Mandal.id)
        .join(Constituency, Mandal.constituency_id == Constituency.id)
        .where((func.lower(Village.name).like(pattern)) | (Village.name_te.like(q.strip() + "%")))
        .order_by(Village.name)
        .limit(limit)
    ).all()
    return [
        VillageHit(id=v.id, name=v.name, name_te=v.name_te, mandal_id=m.id, mandal_name=m.name, constituency_id=c.id, constituency_name=c.name)
        for v, m, c in rows
    ]
