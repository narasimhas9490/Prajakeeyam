"""Database models. Plain String columns (not PG enums) so SQLite tests behave like Postgres."""
from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, ForeignKey, Index, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

CATEGORIES = ("road", "water", "electricity", "drainage", "health", "school", "ration", "pension", "garbage", "other")
STATUSES = ("open", "in_progress", "resolved")
REPORT_REASONS = ("spam", "abuse", "false", "other")
ROLES = ("user", "admin")


def utcnow() -> datetime:
    """Naive UTC timestamps everywhere (stored as TIMESTAMP, serialised with a Z suffix)."""
    return datetime.now(timezone.utc).replace(tzinfo=None)


class Base(DeclarativeBase):
    pass


# ---------------------------------------------------------------- locations
class District(Base):
    __tablename__ = "districts"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=False)
    name: Mapped[str] = mapped_column(String(80))
    name_te: Mapped[str | None] = mapped_column(String(120))


class Constituency(Base):
    __tablename__ = "constituencies"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=False)  # = AC number
    district_id: Mapped[int] = mapped_column(ForeignKey("districts.id"), index=True)
    name: Mapped[str] = mapped_column(String(80))
    name_te: Mapped[str | None] = mapped_column(String(120))
    reservation: Mapped[str] = mapped_column(String(8), default="None")


class Mandal(Base):
    __tablename__ = "mandals"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=False)  # LGD sub-district code
    constituency_id: Mapped[int] = mapped_column(ForeignKey("constituencies.id"), index=True)
    name: Mapped[str] = mapped_column(String(80))
    name_te: Mapped[str | None] = mapped_column(String(120))
    kind: Mapped[str] = mapped_column(String(8), default="rural")  # rural | urban


class Village(Base):
    __tablename__ = "villages"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=False)  # LGD village code
    mandal_id: Mapped[int] = mapped_column(ForeignKey("mandals.id"), index=True)
    name: Mapped[str] = mapped_column(String(100), index=True)
    name_te: Mapped[str | None] = mapped_column(String(150))


class Meta(Base):
    __tablename__ = "meta"
    key: Mapped[str] = mapped_column(String(40), primary_key=True)
    value: Mapped[str] = mapped_column(Text)


# ---------------------------------------------------------------- people & content
class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    google_sub: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    email: Mapped[str | None] = mapped_column(String(254))
    name: Mapped[str] = mapped_column(String(80))
    picture_url: Mapped[str | None] = mapped_column(String(500))
    home_village_id: Mapped[int | None] = mapped_column(ForeignKey("villages.id"))
    role: Mapped[str] = mapped_column(String(8), default="user")
    is_banned: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    last_login_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Problem(Base):
    __tablename__ = "problems"
    __table_args__ = (
        Index("ix_problems_village_feed", "village_id", "created_at", "id"),
        Index("ix_problems_mandal_feed", "mandal_id", "created_at", "id"),
        Index("ix_problems_constituency_feed", "constituency_id", "created_at", "id"),
    )
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    village_id: Mapped[int | None] = mapped_column(ForeignKey("villages.id"))
    mandal_id: Mapped[int] = mapped_column(ForeignKey("mandals.id"))
    constituency_id: Mapped[int] = mapped_column(ForeignKey("constituencies.id"))
    category: Mapped[str] = mapped_column(String(16))
    title: Mapped[str] = mapped_column(String(120))
    description: Mapped[str] = mapped_column(Text)
    photo_url: Mapped[str | None] = mapped_column(String(500))
    status: Mapped[str] = mapped_column(String(16), default="open")
    upvote_count: Mapped[int] = mapped_column(Integer, default=0)
    comment_count: Mapped[int] = mapped_column(Integer, default=0)
    report_count: Mapped[int] = mapped_column(Integer, default=0)
    is_hidden: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, onupdate=utcnow)


class Upvote(Base):
    __tablename__ = "upvotes"
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    problem_id: Mapped[int] = mapped_column(ForeignKey("problems.id"), primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Comment(Base):
    __tablename__ = "comments"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    problem_id: Mapped[int] = mapped_column(ForeignKey("problems.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    body: Mapped[str] = mapped_column(String(1000))
    is_hidden: Mapped[bool] = mapped_column(Boolean, default=False)
    report_count: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Report(Base):
    __tablename__ = "reports"
    __table_args__ = (UniqueConstraint("reporter_id", "target_type", "target_id", name="uq_report_once"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    reporter_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    target_type: Mapped[str] = mapped_column(String(8))  # problem | comment
    target_id: Mapped[int] = mapped_column(Integer)
    reason: Mapped[str] = mapped_column(String(16))
    note: Mapped[str | None] = mapped_column(String(300))
    resolved: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Block(Base):
    __tablename__ = "blocks"
    blocker_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    blocked_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
