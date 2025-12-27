import hashlib
import time
from typing import Dict, Tuple

from config import settings
from models.schemas import TrackResponse


class CacheService:
    """
    Simple in-memory TTL cache for stream URLs.
    Note: Cache is per-instance and lost on container restart.
    Acceptable for personal use with low traffic.
    """

    def __init__(self):
        # Dict of cache_key -> (TrackResponse, cached_at_timestamp)
        self._cache: Dict[str, Tuple[TrackResponse, float]] = {}

    def _cache_key(self, query: str) -> str:
        """Generate consistent cache key from search query."""
        normalized = query.lower().strip()
        return hashlib.sha256(normalized.encode()).hexdigest()[:16]

    def get(self, query: str) -> TrackResponse | None:
        """Get cached response for a search query."""
        key = self._cache_key(query)

        if key not in self._cache:
            return None

        response, cached_at = self._cache[key]

        # Check if cache entry is stale
        if time.time() - cached_at > settings.cache_ttl_seconds:
            del self._cache[key]
            return None

        # Check if stream URL is expired (with 30min buffer)
        if response.expires_at < time.time() + 1800:
            del self._cache[key]
            return None

        return response

    def set(self, query: str, response: TrackResponse) -> None:
        """Cache a response."""
        key = self._cache_key(query)
        self._cache[key] = (response, time.time())

    def clear_expired(self) -> None:
        """Remove expired entries (call periodically if needed)."""
        now = time.time()
        expired_keys = [
            k
            for k, (resp, cached_at) in self._cache.items()
            if now - cached_at > settings.cache_ttl_seconds or resp.expires_at < now + 1800
        ]
        for k in expired_keys:
            del self._cache[k]


