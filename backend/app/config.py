"""Application settings, read from environment variables (or backend/.env)."""
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    env: str = "dev"  # dev | production
    database_url: str = "sqlite:///./dev.db"
    jwt_secret: str = "dev-secret-change-me"
    jwt_days: int = 90
    google_web_client_id: str = ""
    cloudinary_cloud_name: str = ""
    admin_emails: str = ""  # comma separated
    dev_fake_auth: bool = False
    locations_path: str = "../data/ap_locations.json"
    cors_origins: str = "*"

    @property
    def admin_email_set(self) -> set[str]:
        return {e.strip().lower() for e in self.admin_emails.split(",") if e.strip()}

    @property
    def is_production(self) -> bool:
        return self.env.lower() == "production"


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
