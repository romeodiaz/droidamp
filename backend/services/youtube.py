import re
import time

import yt_dlp

from config import settings
from models.schemas import TrackResponse

# Reusable yt-dlp options
# Note: Don't use custom User-Agent for extraction - it can cause empty results
YDL_OPTS = {
    "format": "bestaudio[acodec=opus]/bestaudio",  # Prefer Opus
    "quiet": True,
    "no_warnings": True,
    "extract_flat": False,
    "geo_bypass": True,
    "socket_timeout": 10,
}


class ExtractionError(Exception):
    pass


class NotFoundError(Exception):
    pass


class YouTubeService:
    def search_and_extract(self, query: str) -> TrackResponse:
        """
        Search YouTube and extract stream URL for first result.
        Appends 'official audio' to prioritize music uploads.
        """
        search_query = f"ytsearch1:{query} official audio"

        with yt_dlp.YoutubeDL(YDL_OPTS) as ydl:
            try:
                info = ydl.extract_info(search_query, download=False)
            except yt_dlp.utils.DownloadError as e:
                raise ExtractionError(f"yt-dlp failed: {str(e)}")

        if not info or "entries" not in info:
            raise NotFoundError("No results found")

        # entries can be a generator, convert to list
        entries = list(info["entries"])
        if not entries:
            raise NotFoundError("No results found")

        entry = entries[0]
        return self._parse_entry(entry)

    def extract_by_id(self, video_id: str) -> TrackResponse:
        """Extract stream URL for a specific video ID (for refresh)."""
        url = f"https://www.youtube.com/watch?v={video_id}"

        with yt_dlp.YoutubeDL(YDL_OPTS) as ydl:
            try:
                info = ydl.extract_info(url, download=False)
            except yt_dlp.utils.DownloadError as e:
                raise ExtractionError(f"yt-dlp failed: {str(e)}")

        return self._parse_entry(info)

    def _parse_entry(self, entry: dict) -> TrackResponse:
        """Parse yt-dlp info dict into TrackResponse."""
        # Find best audio format
        formats = entry.get("formats", [])
        audio_formats = [
            f
            for f in formats
            if f.get("acodec") != "none" and f.get("vcodec") == "none"
        ]

        if not audio_formats:
            # Fallback: get URL from entry directly (less reliable)
            stream_url = entry.get("url")
            if not stream_url:
                raise ExtractionError("No audio stream found")
        else:
            # Prefer Opus, fallback to highest quality
            opus_formats = [f for f in audio_formats if f.get("acodec") == "opus"]
            best = opus_formats[0] if opus_formats else audio_formats[0]
            stream_url = best["url"]

        # Calculate expiration (conservative: 4 hours from now)
        expires_at = int(time.time()) + 14400

        # Parse artist from uploader or title
        title = entry.get("title", "Unknown")
        artist = (
            entry.get("artist") or entry.get("uploader") or self._extract_artist(title)
        )

        return TrackResponse(
            video_id=entry["id"],
            stream_url=stream_url,
            expires_at=expires_at,
            title=self._clean_title(title),
            artist=artist,
            thumbnail=entry.get("thumbnail", ""),
            duration_ms=int(entry.get("duration", 0) * 1000),
            skip_segments=[],  # Empty in MVP - populated in Phase 4
            user_agent=settings.user_agent,
        )

    def _extract_artist(self, title: str) -> str | None:
        """Try to extract artist from 'Artist - Song' format."""
        if " - " in title:
            return title.split(" - ")[0].strip()
        return None

    def _clean_title(self, title: str) -> str:
        """Remove common suffixes like (Official Audio), [Lyrics], etc."""
        patterns = [
            r"\s*\(Official\s*(Audio|Video|Music Video|Lyrics?)\)",
            r"\s*\[Official\s*(Audio|Video|Music Video|Lyrics?)\]",
            r"\s*\(Lyrics?\)",
            r"\s*\[Lyrics?\]",
            r"\s*\(Audio\)",
            r"\s*\[Audio\]",
        ]
        cleaned = title
        for pattern in patterns:
            cleaned = re.sub(pattern, "", cleaned, flags=re.IGNORECASE)
        return cleaned.strip()

