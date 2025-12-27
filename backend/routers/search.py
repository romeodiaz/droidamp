from fastapi import APIRouter, Header, HTTPException, Query

from config import settings
from models.schemas import ErrorResponse, TrackResponse
from services.cache import CacheService
from services.youtube import ExtractionError, NotFoundError, YouTubeService

router = APIRouter(prefix="/search", tags=["search"])

youtube = YouTubeService()
cache = CacheService()


def verify_api_key(x_api_key: str = Header(...)):
    """Simple API key authentication."""
    if x_api_key != settings.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")


@router.get("/lucky", response_model=TrackResponse)
async def search_lucky(
    q: str = Query(..., min_length=1, max_length=200, description="Search query"),
    x_api_key: str = Header(...),
):
    """
    'I'm Feeling Lucky' search - returns first result ready to play.

    The response includes:
    - stream_url: Direct audio URL (use with provided user_agent)
    - expires_at: Unix timestamp when URL expires (re-search before this)
    - skip_segments: Empty in MVP (SponsorBlock added in Phase 4)
    """
    verify_api_key(x_api_key)

    # Check cache first
    cached = cache.get(q)
    if cached:
        return cached

    # Extract from YouTube
    try:
        track = youtube.search_and_extract(q)
    except NotFoundError:
        raise HTTPException(
            status_code=404,
            detail=ErrorResponse(
                error="No results found",
                code="NOT_FOUND",
            ).model_dump(),
        )
    except ExtractionError as e:
        raise HTTPException(
            status_code=502,
            detail=ErrorResponse(
                error=str(e),
                code="EXTRACTION_FAILED",
                retry_after=60,
            ).model_dump(),
        )

    # Cache the result
    cache.set(q, track)

    return track

