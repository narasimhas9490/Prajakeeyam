from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from .. import security
from ..config import settings
from ..db import get_db
from ..models import User, utcnow
from ..ratelimit import limiter
from ..schemas import DevLoginIn, GoogleLoginIn, TokenOut, UserOut, z

router = APIRouter(prefix="/auth", tags=["auth"])


def _issue(db: Session, *, sub: str, email: str | None, name: str, picture: str | None) -> TokenOut:
    user = db.scalar(select(User).where(User.google_sub == sub))
    if user is None:
        user = User(google_sub=sub, email=email, name=(name or "User")[:80], picture_url=picture)
        db.add(user)
    else:
        user.email = email or user.email
        user.picture_url = picture or user.picture_url
        user.last_login_at = utcnow()
    if email and email.lower() in settings.admin_email_set:
        user.role = "admin"
    db.flush()
    if user.is_banned:
        db.rollback()
        raise HTTPException(status_code=403, detail="This account is suspended")
    db.commit()
    db.refresh(user)
    token, exp = security.create_access_token(user.id)
    return TokenOut(access_token=token, expires_at=z(exp.replace(tzinfo=None)), user=UserOut.model_validate(user))


@router.post("/firebase", response_model=TokenOut)
def firebase_login(body: GoogleLoginIn, db: Session = Depends(get_db)):
    """Exchange a Firebase Auth ID token (Google provider) for our JWT."""
    limiter.check("auth:firebase", 60, 60)
    try:
        claims = security.verify_firebase_id_token(body.id_token)
    except ValueError as exc:
        raise HTTPException(status_code=401, detail=f"Invalid Firebase token: {exc}") from exc
    uid = claims.get("sub") or claims.get("user_id")
    return _issue(
        db,
        sub=f"firebase:{uid}",
        email=claims.get("email"),
        name=claims.get("name") or (claims.get("email") or "User").split("@")[0],
        picture=claims.get("picture"),
    )


@router.post("/google", response_model=TokenOut)
def google_login(body: GoogleLoginIn, db: Session = Depends(get_db)):
    """Exchange a raw Google ID token (Credential Manager without Firebase) for our JWT."""
    limiter.check("auth:google", 60, 60)
    try:
        claims = security.verify_google_id_token(body.id_token)
    except ValueError as exc:
        raise HTTPException(status_code=401, detail=f"Invalid Google token: {exc}") from exc
    return _issue(
        db,
        sub=claims["sub"],
        email=claims.get("email"),
        name=claims.get("name") or claims.get("given_name") or "User",
        picture=claims.get("picture"),
    )


@router.post("/dev", response_model=TokenOut, include_in_schema=False)
def dev_login(body: DevLoginIn, db: Session = Depends(get_db)):
    """Emulator/test login without Google. Only exists when DEV_FAKE_AUTH=1 and ENV != production."""
    if settings.is_production or not settings.dev_fake_auth:
        raise HTTPException(status_code=404, detail="Not found")
    return _issue(db, sub=f"dev:{body.sub}", email=body.email, name=body.name, picture=None)
