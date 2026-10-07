"""Opaque keyset-pagination cursors."""
import base64

from fastapi import HTTPException


def encode_cursor(*parts) -> str:
    raw = "|".join(str(p) for p in parts).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def decode_cursor(cursor: str, expected_parts: int) -> list[str]:
    try:
        padded = cursor + "=" * (-len(cursor) % 4)
        parts = base64.urlsafe_b64decode(padded).decode().split("|")
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=422, detail="Bad cursor") from exc
    if len(parts) != expected_parts:
        raise HTTPException(status_code=422, detail="Bad cursor")
    return parts
