import subprocess

from fastapi import APIRouter

from models.schemas import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint for Cloud Run."""
    try:
        result = subprocess.run(
            ["yt-dlp", "--version"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        version = result.stdout.strip()
    except Exception:
        version = "unknown"

    return HealthResponse(status="healthy", ytdlp_version=version)


