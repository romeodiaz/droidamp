from fastapi import APIRouter, Header, HTTPException, Query

from config import settings
from models.schemas import ErrorResponse, MixPlaylistResponse, TrackResponse
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


from pydantic import BaseModel
from typing import List, Optional

class NextTrackRequest(BaseModel):
    video_id: str
    exclude: Optional[List[str]] = None


@router.get("/mix", response_model=MixPlaylistResponse)
async def get_mix_playlist(
    video_id: str = Query(..., min_length=1, max_length=20, description="Video ID to create mix from"),
    x_api_key: str = Header(...),
):
    """
    Get YouTube Mix playlist for a video.
    Returns list of ~25 video IDs that can be used as a queue.
    Call this once when first song plays, then use /track to get each song.
    """
    verify_api_key(x_api_key)
    
    # Check cache
    cache_key = f"mix:{video_id}"
    cached = cache.get(cache_key)
    if cached:
        return cached

    try:
        mix = youtube.get_mix_playlist(video_id)
    except NotFoundError:
        raise HTTPException(
            status_code=404,
            detail=ErrorResponse(error="Could not create mix", code="NOT_FOUND").model_dump(),
        )
    except ExtractionError as e:
        raise HTTPException(
            status_code=502,
            detail=ErrorResponse(error=str(e), code="EXTRACTION_FAILED").model_dump(),
        )
    
    cache.set(cache_key, mix)
    return mix


@router.get("/track", response_model=TrackResponse)
async def get_track_by_id(
    video_id: str = Query(..., min_length=1, max_length=20, description="Video ID to extract"),
    x_api_key: str = Header(...),
):
    """
    Get track info for a specific video ID.
    Use this with the queue from /mix to get each track.
    """
    verify_api_key(x_api_key)
    
    # Check cache
    cache_key = f"track:{video_id}"
    cached = cache.get(cache_key)
    if cached:
        return cached

    try:
        track = youtube.extract_by_id(video_id)
    except NotFoundError:
        raise HTTPException(
            status_code=404,
            detail=ErrorResponse(error="Video not found", code="NOT_FOUND").model_dump(),
        )
    except ExtractionError as e:
        raise HTTPException(
            status_code=502,
            detail=ErrorResponse(error=str(e), code="EXTRACTION_FAILED").model_dump(),
        )
    
    cache.set(cache_key, track)
    return track


@router.post("/next", response_model=TrackResponse)
async def get_next_track(
    request: NextTrackRequest,
    x_api_key: str = Header(...),
):
    """
    Get the next recommended track based on YouTube's suggestions.
    Uses YouTube's Mix/Radio feature for continuous playback.
    """
    verify_api_key(x_api_key)
    
    video_id = request.video_id
    exclude_ids = set(request.exclude) if request.exclude else set()

    # Skip cache when exclude list is large (personalized)
    cache_key = f"next:{video_id}"
    if len(exclude_ids) < 5:
        cached = cache.get(cache_key)
        if cached and cached.video_id not in exclude_ids:
            return cached

    try:
        track = youtube.get_next_track(video_id, exclude_ids)
    except NotFoundError:
        raise HTTPException(
            status_code=404,
            detail=ErrorResponse(
                error="No recommendations found",
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
    cache.set(cache_key, track)

    return track


