import secrets

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import update
from sqlalchemy.orm import Session

from ..db import get_db
from ..deps import get_current_user
from ..models import Block, Comment, Problem, User, Village
from ..schemas import BlockOut, MeUpdate, UserOut

router = APIRouter(tags=["users"])


@router.get("/me", response_model=UserOut)
def me(user: User = Depends(get_current_user)):
    return user


@router.patch("/me", response_model=UserOut)
def update_me(body: MeUpdate, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if body.name is not None:
        user.name = body.name.strip()
    if "home_village_id" in body.model_fields_set:
        if body.home_village_id is not None and db.get(Village, body.home_village_id) is None:
            raise HTTPException(status_code=422, detail="Unknown village")
        user.home_village_id = body.home_village_id
    db.commit()
    db.refresh(user)
    return user


@router.delete("/me", status_code=204)
def delete_me(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Account deletion (Play policy): anonymise the user and hide everything they posted."""
    user.name = "Deleted user"
    user.email = None
    user.picture_url = None
    user.home_village_id = None
    user.google_sub = f"deleted:{user.id}:{secrets.token_hex(8)}"
    db.execute(update(Problem).where(Problem.user_id == user.id).values(is_hidden=True))
    db.execute(update(Comment).where(Comment.user_id == user.id).values(is_hidden=True))
    db.commit()
    return None


@router.post("/users/{user_id}/block", response_model=BlockOut)
def block_user(user_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if user_id == me.id:
        raise HTTPException(status_code=422, detail="Cannot block yourself")
    if db.get(User, user_id) is None:
        raise HTTPException(status_code=404, detail="User not found")
    if db.get(Block, (me.id, user_id)) is None:
        db.add(Block(blocker_id=me.id, blocked_id=user_id))
        db.commit()
    return BlockOut(blocked=True)


@router.delete("/users/{user_id}/block", response_model=BlockOut)
def unblock_user(user_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    block = db.get(Block, (me.id, user_id))
    if block is not None:
        db.delete(block)
        db.commit()
    return BlockOut(blocked=False)
