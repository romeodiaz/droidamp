from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # API Security
    api_key: str  # Required - set via environment variable

    # Cache settings
    cache_ttl_seconds: int = 10800  # 3 hours (stream URLs expire ~6hrs)

    # YouTube settings
    user_agent: str = "Mozilla/5.0 (Linux; Android 14; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    class Config:
        env_file = ".env"


settings = Settings()


