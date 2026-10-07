"""Moderation endpoints (admin role only)."""
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from ..db import get_db
from ..deps import get_admin
from ..models import Comment, Problem, Report, User
from ..schemas import BanIn, HiddenIn, ReportOut, ReportResolveIn, z
from . import locations

router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(get_admin)])


def _target(db: Session, report: Report):
    model = Problem if report.target_type == "problem" else Comment
    return db.get(model, report.target_id)


@router.get("/reports", response_model=list[ReportOut])
def list_reports(resolved: bool = False, limit: int = Query(100, le=500), db: Session = Depends(get_db)):
    reports = db.scalars(select(Report).where(Report.resolved.is_(resolved)).order_by(Report.created_at.desc()).limit(limit)).all()
    out = []
    for r in reports:
        t = _target(db, r)
        snippet = (t.title if isinstance(t, Problem) else t.body) if t else "<deleted>"
        out.append(ReportOut(
            id=r.id, reporter_id=r.reporter_id, target_type=r.target_type, target_id=r.target_id, reason=r.reason,
            note=r.note, snippet=snippet[:140], target_hidden=bool(t and t.is_hidden), created_at=z(r.created_at),
        ))
    return out


@router.post("/reports/{report_id}/resolve")
def resolve_report(report_id: int, body: ReportResolveIn, db: Session = Depends(get_db)):
    r = db.get(Report, report_id)
    if r is None:
        raise HTTPException(status_code=404, detail="Report not found")
    t = _target(db, r)
    if t is not None and body.action in ("hide", "unhide"):
        t.is_hidden = body.action == "hide"
    db.execute(update(Report).where(Report.target_type == r.target_type, Report.target_id == r.target_id).values(resolved=True))
    db.commit()
    return {"ok": True, "action": body.action}


@router.patch("/comments/{comment_id}")
def set_comment_hidden(comment_id: int, body: HiddenIn, db: Session = Depends(get_db)):
    c = db.get(Comment, comment_id)
    if c is None:
        raise HTTPException(status_code=404, detail="Comment not found")
    c.is_hidden = body.is_hidden
    db.commit()
    return {"ok": True, "is_hidden": c.is_hidden}


@router.post("/users/{user_id}/ban")
def set_banned(user_id: int, body: BanIn, db: Session = Depends(get_db)):
    u = db.get(User, user_id)
    if u is None:
        raise HTTPException(status_code=404, detail="User not found")
    u.is_banned = body.banned
    db.commit()
    return {"ok": True, "banned": u.is_banned}


@router.post("/locations/reload")
def reload_locations(db: Session = Depends(get_db)):
    return {"ok": True, "version": locations.refresh_bundle(db)}
