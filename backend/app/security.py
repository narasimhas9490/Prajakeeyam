"""Google ID-token verification and our own JWT issuing."""
from datetime import datetime, timedelta, timezone

import jwt
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token

from .config import settings

GOOGLE_ISSUERS = {"accounts.google.com", "https://accounts.google.com"}


def verify_google_id_token(token: str) -> dict:
    """Return the Google claims (sub, email, name, picture...). Raises ValueError when invalid."""
    if not settings.google_web_client_id:
        raise ValueError("GOOGLE_WEB_CLIENT_ID is not configured on the server")
    claims = google_id_token.verify_oauth2_token(
        token, google_requests.Request(), settings.google_web_client_id, clock_skew_in_seconds=10
    )
    if claims.get("iss") not in GOOGLE_ISSUERS:
        raise ValueError("unexpected token issuer")
    if not claims.get("sub"):
        raise ValueError("token has no subject")
    return claims


def create_access_token(user_id: int) -> tuple[str, datetime]:
    now = datetime.now(timezone.utc)
    exp = now + timedelta(days=settings.jwt_days)
    token = jwt.encode({"sub": str(user_id), "iat": now, "exp": exp}, settings.jwt_secret, algorithm="HS256")
    return token, exp


def decode_access_token(token: str) -> int | None:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
        return int(payload["sub"])
    except (jwt.PyJWTError, KeyError, ValueError, TypeError):
        return None
