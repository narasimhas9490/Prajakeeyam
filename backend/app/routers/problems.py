"""Problems feed, posting, upvotes, comments and reports."""
from datetime import datetime
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import and_, or_, select
from sqlalchemy.orm import Session

from ..config import settings
from ..db import get_db
from ..deps import get_current_user, get_current_user_optional
from ..models import Block, Comment, Mandal, Problem, Report, Upvote, User, Village
from ..paging import decode_cursor, encode_cursor
from ..ratelimit import limiter
from ..schemas import (
    AuthorOut, Category, CommentCreate, CommentOut, CommentPage, ProblemCreate, ProblemOut, ProblemPage,
    ProblemUpdate, ReportIn, ReportResult, Status, UpvoteOut, z,
)

router = APIRouter(tags=["problems"])
AUTO_HIDE_REPORTS = 3


# ------------------------------------------------------------------ helpers
def problem_out(p: Problem, author: User, me: User | None, my_upvote: bool) -> ProblemOut:
    return ProblemOut(
        id=p.id, user_id=p.user_id,
        author=AuthorOut(id=author.id, name=author.name, picture_url=author.picture_url),
        village_id=p.village_id, mandal_id=p.mandal_id, constituency_id=p.constituency_id,
        category=p.category, title=p.title, description=p.description, photo_url=p.photo_url, status=p.status,
        upvote_count=p.upvote_count, comment_count=p.comment_count, created_at=z(p.created_at),
        my_upvote=my_upvote, is_owner=bool(me and me.id == p.user_id), is_hidden=p.is_hidden,
    )


def comment_out(c: Comment, author: User, me: User | None) -> CommentOut:
    return CommentOut(
        id=c.id, problem_id=c.problem_id, body=c.body, created_at=z(c.created_at),
        author=AuthorOut(id=author.id, name=author.name, picture_url=author.picture_url),
        is_owner=bool(me and me.id == c.user_id),
    )


def _validate_photo(url: str | None) -> str | None:
    if not url:
        return None
    prefix = "https://res.cloudinary.com/" + (f"{settings.cloudinary_cloud_name}/" if settings.cloudinary_cloud_name else "")
    if not url.startswith(prefix):
        raise HTTPException(status_code=422, detail="photo_url must be a Cloudinary URL uploaded by this app")
    return url


def _not_blocked(q, me: User | None, author_col):
    if me is None:
        return q
    blocked = select(Block.blocked_id).where(Block.blocker_id == me.id)
    return q.where(author_col.not_in(blocked))


def _my_upvotes(db: Session, me: User | None, ids: list[int]) -> set[int]:
    if me is None or not ids:
        return set()
    return set(db.scalars(select(Upvote.problem_id).where(Upvote.user_id == me.id, Upvote.problem_id.in_(ids))))


def _load_problem(db: Session, problem_id: int, me: User | None) -> tuple[Problem, User]:
    row = db.execute(select(Problem, User).join(User, Problem.user_id == User.id).where(Problem.id == problem_id)).first()
    if row is None:
        raise HTTPException(status_code=404, detail="Problem not found")
    p, author = row
    privileged = me is not None and (me.id == p.user_id or me.role == "admin")
    if (p.is_hidden or author.is_banned) and not privileged:
        raise HTTPException(status_code=404, detail="Problem not found")
    return p, author


def _parse_dt(value: str) -> datetime:
    try:
        return datetime.fromisoformat(value)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail="Bad cursor") from exc


# ------------------------------------------------------------------ feed
@router.get("/problems", response_model=ProblemPage)
def list_problems(
    village_id: int | None = None,
    mandal_id: int | None = None,
    constituency_id: int | None = None,
    status: Status | None = None,
    category: Category | None = None,
    sort: Literal["new", "top"] = "new",
    cursor: str | None = None,
    limit: int = Query(20, ge=1, le=50),
    me: User | None = Depends(get_current_user_optional),
    db: Session = Depends(get_db),
):
    chosen = [(col, val) for col, val in ((Problem.village_id, village_id), (Problem.mandal_id, mandal_id), (Problem.constituency_id, constituency_id)) if val is not None]
    if len(chosen) != 1:
        raise HTTPException(status_code=422, detail="Pass exactly one of village_id, mandal_id, constituency_id")
    col, val = chosen[0]
    q = (
        select(Problem, User)
        .join(User, Problem.user_id == User.id)
        .where(col == val, Problem.is_hidden.is_(False), User.is_banned.is_(False))
    )
    if status:
        q = q.where(Problem.status == status)
    if category:
        q = q.where(Problem.category == category)
    q = _not_blocked(q, me, Problem.user_id)
    if sort == "top":
        if cursor:
            u, t, i = decode_cursor(cursor, 3)
            u, t, i = int(u), _parse_dt(t), int(i)
            q = q.where(or_(Problem.upvote_count < u, and_(Problem.upvote_count == u, Problem.created_at < t), and_(Problem.upvote_count == u, Problem.created_at == t, Problem.id < i)))
        q = q.order_by(Problem.upvote_count.desc(), Problem.created_at.desc(), Problem.id.desc())
    else:
        if cursor:
            t, i = decode_cursor(cursor, 2)
            t, i = _parse_dt(t), int(i)
            q = q.where(or_(Problem.created_at < t, and_(Problem.created_at == t, Problem.id < i)))
        q = q.order_by(Problem.created_at.desc(), Problem.id.desc())
    rows = db.execute(q.limit(limit + 1)).all()
    has_more = len(rows) > limit
    rows = rows[:limit]
    mine = _my_upvotes(db, me, [p.id for p, _ in rows])
    next_cursor = None
    if has_more:
        last = rows[-1][0]
        next_cursor = (
            encode_cursor(last.upvote_count, last.created_at.isoformat(), last.id)
            if sort == "top" else encode_cursor(last.created_at.isoformat(), last.id)
        )
    return ProblemPage(items=[problem_out(p, a, me, p.id in mine) for p, a in rows], next_cursor=next_cursor)


@router.post("/problems", response_model=ProblemOut, status_code=201)
def create_problem(body: ProblemCreate, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    limiter.check(f"problem:{me.id}", 5, 600)
    mandal = db.get(Mandal, body.mandal_id)
    if mandal is None:
        raise HTTPException(status_code=404, detail="Unknown mandal")
    if body.village_id is not None:
        village = db.get(Village, body.village_id)
        if village is None or village.mandal_id != mandal.id:
            raise HTTPException(status_code=422, detail="Village does not belong to that mandal")
    p = Problem(
        user_id=me.id, village_id=body.village_id, mandal_id=mandal.id, constituency_id=mandal.constituency_id,
        category=body.category, title=body.title.strip(), description=body.description.strip(),
        photo_url=_validate_photo(body.photo_url),
    )
    db.add(p)
    db.commit()
    db.refresh(p)
    return problem_out(p, me, me, False)


@router.get("/problems/{problem_id}", response_model=ProblemOut)
def get_problem(problem_id: int, me: User | None = Depends(get_current_user_optional), db: Session = Depends(get_db)):
    p, author = _load_problem(db, problem_id, me)
    return problem_out(p, author, me, p.id in _my_upvotes(db, me, [p.id]))


@router.patch("/problems/{problem_id}", response_model=ProblemOut)
def update_problem(problem_id: int, body: ProblemUpdate, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    p, author = _load_problem(db, problem_id, me)
    is_admin, is_owner = me.role == "admin", me.id == p.user_id
    if not (is_admin or is_owner):
        raise HTTPException(status_code=403, detail="Only the author or an admin can edit this")
    if body.title is not None:
        p.title = body.title.strip()
    if body.description is not None:
        p.description = body.description.strip()
    if body.status is not None:
        if not is_admin and body.status not in ("open", "resolved"):
            raise HTTPException(status_code=403, detail="Only admins can set that status")
        p.status = body.status
    if body.is_hidden is not None:
        if not is_admin and body.is_hidden is False:
            raise HTTPException(status_code=403, detail="Only admins can un-hide")
        p.is_hidden = body.is_hidden
    db.commit()
    db.refresh(p)
    return problem_out(p, author, me, p.id in _my_upvotes(db, me, [p.id]))


@router.post("/problems/{problem_id}/upvote", response_model=UpvoteOut)
def toggle_upvote(problem_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    limiter.check(f"upvote:{me.id}", 60, 60)
    p, _ = _load_problem(db, problem_id, me)
    existing = db.get(Upvote, (me.id, p.id))
    if existing is not None:
        db.delete(existing)
        p.upvote_count = max(0, p.upvote_count - 1)
        upvoted = False
    else:
        db.add(Upvote(user_id=me.id, problem_id=p.id))
        p.upvote_count += 1
        upvoted = True
    db.commit()
    return UpvoteOut(upvoted=upvoted, upvote_count=p.upvote_count)


# ------------------------------------------------------------------ comments
@router.get("/problems/{problem_id}/comments", response_model=CommentPage)
def list_comments(
    problem_id: int, cursor: str | None = None, limit: int = Query(30, ge=1, le=100),
    me: User | None = Depends(get_current_user_optional), db: Session = Depends(get_db),
):
    _load_problem(db, problem_id, me)
    q = (
        select(Comment, User)
        .join(User, Comment.user_id == User.id)
        .where(Comment.problem_id == problem_id, Comment.is_hidden.is_(False), User.is_banned.is_(False))
    )
    q = _not_blocked(q, me, Comment.user_id)
    if cursor:
        t, i = decode_cursor(cursor, 2)
        t, i = _parse_dt(t), int(i)
        q = q.where(or_(Comment.created_at > t, and_(Comment.created_at == t, Comment.id > i)))
    rows = db.execute(q.order_by(Comment.created_at.asc(), Comment.id.asc()).limit(limit + 1)).all()
    has_more = len(rows) > limit
    rows = rows[:limit]
    next_cursor = encode_cursor(rows[-1][0].created_at.isoformat(), rows[-1][0].id) if has_more else None
    return CommentPage(items=[comment_out(c, a, me) for c, a in rows], next_cursor=next_cursor)


@router.post("/problems/{problem_id}/comments", response_model=CommentOut, status_code=201)
def add_comment(problem_id: int, body: CommentCreate, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    limiter.check(f"comment:{me.id}", 20, 600)
    p, _ = _load_problem(db, problem_id, me)
    c = Comment(problem_id=p.id, user_id=me.id, body=body.body.strip())
    p.comment_count += 1
    db.add(c)
    db.commit()
    db.refresh(c)
    return comment_out(c, me, me)


# ------------------------------------------------------------------ reports
def _file_report(db: Session, me: User, target_type: str, target: Problem | Comment, body: ReportIn) -> ReportResult:
    if target.user_id == me.id:
        raise HTTPException(status_code=422, detail="You cannot report your own post")
    existing = db.scalar(select(Report).where(Report.reporter_id == me.id, Report.target_type == target_type, Report.target_id == target.id))
    if existing is not None:
        return ReportResult(reported=True, already=True)
    db.add(Report(reporter_id=me.id, target_type=target_type, target_id=target.id, reason=body.reason, note=body.note))
    target.report_count += 1
    if target.report_count >= AUTO_HIDE_REPORTS and not target.is_hidden:
        target.is_hidden = True
    db.commit()
    return ReportResult(reported=True, already=False)


@router.post("/problems/{problem_id}/report", response_model=ReportResult, status_code=201)
def report_problem(problem_id: int, body: ReportIn, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    limiter.check(f"report:{me.id}", 20, 3600)
    p, _ = _load_problem(db, problem_id, me)
    return _file_report(db, me, "problem", p, body)


@router.post("/comments/{comment_id}/report", response_model=ReportResult, status_code=201)
def report_comment(comment_id: int, body: ReportIn, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    limiter.check(f"report:{me.id}", 20, 3600)
    c = db.get(Comment, comment_id)
    if c is None or c.is_hidden:
        raise HTTPException(status_code=404, detail="Comment not found")
    return _file_report(db, me, "comment", c, body)
