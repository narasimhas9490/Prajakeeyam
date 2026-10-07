"""Pydantic request/response models."""
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Category = Literal["road", "water", "electricity", "drainage", "health", "school", "ration", "pension", "garbage", "other"]
Status = Literal["open", "in_progress", "resolved"]
Reason = Literal["spam", "abuse", "false", "other"]


def z(dt: datetime) -> str:
    """Serialise a naive-UTC datetime as ISO-8601 with a Z suffix."""
    return dt.replace(microsecond=0).isoformat() + "Z"


# ------------------------------------------------------------------ users / auth
class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    name: str
    email: str | None = None
    picture_url: str | None = None
    home_village_id: int | None = None
    role: str


class AuthorOut(BaseModel):
    id: int
    name: str
    picture_url: str | None = None


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_at: str
    user: UserOut


class GoogleLoginIn(BaseModel):
    id_token: str = Field(min_length=20)


class DevLoginIn(BaseModel):
    sub: str = Field(min_length=1, max_length=40)
    name: str = Field("Dev User", max_length=80)
    email: str | None = None


class MeUpdate(BaseModel):
    name: str | None = Field(None, min_length=1, max_length=80)
    home_village_id: int | None = None


# ------------------------------------------------------------------ problems
class ProblemCreate(BaseModel):
    mandal_id: int
    village_id: int | None = None
    category: Category
    title: str = Field(min_length=3, max_length=120)
    description: str = Field(min_length=3, max_length=2000)
    photo_url: str | None = Field(None, max_length=500)


class ProblemUpdate(BaseModel):
    title: str | None = Field(None, min_length=3, max_length=120)
    description: str | None = Field(None, min_length=3, max_length=2000)
    status: Status | None = None
    is_hidden: bool | None = None


class ProblemOut(BaseModel):
    id: int
    user_id: int
    author: AuthorOut
    village_id: int | None
    mandal_id: int
    constituency_id: int
    category: str
    title: str
    description: str
    photo_url: str | None
    status: str
    upvote_count: int
    comment_count: int
    created_at: str
    my_upvote: bool = False
    is_owner: bool = False
    is_hidden: bool = False


class ProblemPage(BaseModel):
    items: list[ProblemOut]
    next_cursor: str | None = None


class UpvoteOut(BaseModel):
    upvoted: bool
    upvote_count: int


# ------------------------------------------------------------------ comments / reports / blocks
class CommentCreate(BaseModel):
    body: str = Field(min_length=1, max_length=1000)


class CommentOut(BaseModel):
    id: int
    problem_id: int
    author: AuthorOut
    body: str
    created_at: str
    is_owner: bool = False


class CommentPage(BaseModel):
    items: list[CommentOut]
    next_cursor: str | None = None


class ReportIn(BaseModel):
    reason: Reason
    note: str | None = Field(None, max_length=300)


class ReportResult(BaseModel):
    reported: bool
    already: bool = False


class BlockOut(BaseModel):
    blocked: bool


# ------------------------------------------------------------------ locations
class VillageHit(BaseModel):
    id: int
    name: str
    name_te: str | None
    mandal_id: int
    mandal_name: str
    constituency_id: int
    constituency_name: str


# ------------------------------------------------------------------ admin
class ReportOut(BaseModel):
    id: int
    reporter_id: int
    target_type: str
    target_id: int
    reason: str
    note: str | None
    snippet: str
    target_hidden: bool
    created_at: str


class ReportResolveIn(BaseModel):
    action: Literal["hide", "unhide", "dismiss"]


class HiddenIn(BaseModel):
    is_hidden: bool


class BanIn(BaseModel):
    banned: bool
