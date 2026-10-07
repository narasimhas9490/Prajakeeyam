#!/usr/bin/env python
"""Promote a user (who has signed in at least once) to admin:  python scripts/make_admin.py you@gmail.com"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from sqlalchemy import select  # noqa: E402

from app.db import SessionLocal  # noqa: E402
from app.models import User  # noqa: E402


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    email = sys.argv[1].strip().lower()
    with SessionLocal() as db:
        user = db.scalar(select(User).where(User.email == email))
        if user is None:
            raise SystemExit(f"No user with email {email} has signed in yet (or add it to ADMIN_EMAILS instead)")
        user.role = "admin"
        db.commit()
        print(f"{user.name} (#{user.id}) is now admin")


if __name__ == "__main__":
    main()
