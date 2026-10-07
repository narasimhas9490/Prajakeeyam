"""Prajakeeyam API - FastAPI entry point."""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .config import settings
from .db import SessionLocal, engine
from .models import Base
from .routers import admin, auth, health, locations, problems, users


@asynccontextmanager
async def lifespan(_app: FastAPI):
    Base.metadata.create_all(bind=engine)
    with SessionLocal() as db:
        try:
            locations.refresh_bundle(db)
        except Exception:  # noqa: BLE001 - DB may be empty on first boot; bundle is lazy anyway
            pass
    yield


app = FastAPI(
    title="Prajakeeyam API",
    version="0.1.0",
    description="Public, village-level problem tracker for Andhra Pradesh.",
    lifespan=lifespan,
    docs_url=None if settings.is_production else "/docs",
    redoc_url=None,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in settings.cors_origins.split(",") if o.strip()],
    allow_methods=["*"],
    allow_headers=["*"],
)
for r in (health.router, locations.router, auth.router, users.router, problems.router, admin.router):
    app.include_router(r)


@app.get("/", include_in_schema=False)
def root():
    return {"name": "Prajakeeyam API", "health": "/health"}
