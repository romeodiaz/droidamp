from pydantic import BaseModel


class SkipSegment(BaseModel):
    start_ms: int
    end_ms: int
    category: str  # "sponsor", "intro", "outro", "selfpromo", etc.


class TrackResponse(BaseModel):
    video_id: str
    stream_url: str
    expires_at: int  # Unix timestamp
    title: str
    artist: str | None
    thumbnail: str
    duration_ms: int
    skip_segments: list[SkipSegment]  # Empty in MVP, populated in Phase 4
    user_agent: str  # Client MUST use this for playback


class ErrorResponse(BaseModel):
    error: str
    code: str  # "NOT_FOUND", "EXTRACTION_FAILED", "RATE_LIMITED", etc.
    retry_after: int | None = None  # Seconds to wait before retry


class HealthResponse(BaseModel):
    status: str
    ytdlp_version: str


class MixPlaylistResponse(BaseModel):
    video_ids: list[str]
    mix_id: str


