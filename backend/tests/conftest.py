import os
import sys
from pathlib import Path

# Must be set before `app` is imported: settings are read at import time.
os.environ["DATABASE_URL"] = "sqlite+pysqlite:///:memory:"
os.environ["DEV_FAKE_AUTH"] = "1"
os.environ["ENV"] = "dev"
os.environ["CLOUDINARY_CLOUD_NAME"] = "demo"
os.environ["ADMIN_EMAILS"] = "admin@example.com"
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.db import SessionLocal, engine  # noqa: E402
from app.main import app  # noqa: E402
from app.models import Base, Constituency, District, Mandal, Meta, Village  # noqa: E402


@pytest.fixture(scope="session")
def client():
    Base.metadata.create_all(bind=engine)
    with SessionLocal() as db:
        # insert in FK order (no ORM relationships are declared, so flush between levels)
        db.add(District(id=1, name="Guntur", name_te="గుంటూరు"))
        db.flush()
        db.add(Constituency(id=96, district_id=1, name="Ponnur", name_te="పొన్నూరు", reservation="None"))
        db.flush()
        db.add_all([Mandal(id=4512, constituency_id=96, name="Chebrolu", name_te="చేబ్రోలు", kind="rural"), Mandal(id=4513, constituency_id=96, name="Ponnur", kind="rural")])
        db.flush()
        db.add_all([
            Village(id=100, mandal_id=4512, name="Godavarru", name_te="గొడవర్రు"),
            Village(id=101, mandal_id=4512, name="Sekuru"),
            Village(id=102, mandal_id=4513, name="Nidubrolu"),
            Meta(key="locations_version", value="test-1"),
        ])
        db.commit()
    with TestClient(app) as c:
        yield c


def login(client: TestClient, sub: str, name: str = "User", email: str | None = None) -> dict[str, str]:
    r = client.post("/auth/dev", json={"sub": sub, "name": name, "email": email})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}
