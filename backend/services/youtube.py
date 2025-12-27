import re
import time

import yt_dlp

from config import settings
from models.schemas import MixPlaylistResponse, TrackResponse

# Reusable yt-dlp options
# Note: Don't use custom User-Agent for extraction - it can cause empty results
YDL_OPTS = {
    "format": "bestaudio[acodec=opus]/bestaudio",  # Prefer Opus
    "quiet": True,
    "no_warnings": True,
    "extract_flat": False,
    "geo_bypass": True,
    "socket_timeout": 15,
    "skip_download": True,
    "no_check_certificates": True,
}

# Options for quickly listing mix playlist (flat = fast, IDs only)
YDL_FLAT_OPTS = {
    "quiet": True,
    "no_warnings": True,
    "extract_flat": True,
    "geo_bypass": True,
    "socket_timeout": 8,
    "playlistend": 25,  # Only get first 25 items
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

    def get_mix_playlist(self, video_id: str) -> MixPlaylistResponse:
        """Get YouTube Mix playlist for a video (fast flat extraction).
        
        Returns list of video IDs in the mix, which can be used for queue.
        """
        mix_url = f"https://www.youtube.com/watch?v={video_id}&list=RD{video_id}"
        
        with yt_dlp.YoutubeDL(YDL_FLAT_OPTS) as ydl:
            try:
                info = ydl.extract_info(mix_url, download=False)
            except yt_dlp.utils.DownloadError as e:
                raise ExtractionError(f"yt-dlp failed: {str(e)}")
        
        if not info:
            raise NotFoundError("Could not get mix playlist")
        
        entries = info.get("entries", [])
        if not entries:
            raise NotFoundError("Empty mix playlist")
        
        video_ids = [e.get("id") for e in entries if e and e.get("id")]
        
        return MixPlaylistResponse(
            video_ids=video_ids,
            mix_id=f"RD{video_id}"
        )

    def get_next_track(self, video_id: str, exclude_ids: set[str] | None = None) -> TrackResponse:
        """Get the next recommended track based on YouTube's suggestions.
        
        Two-step approach for speed:
        1. Flat extraction to quickly get candidate IDs
        2. Full extraction for just the selected track
        
        Args:
            video_id: Current video ID
            exclude_ids: Set of video IDs to skip (already played)
        """
        if exclude_ids is None:
            exclude_ids = set()
        
        # Always exclude current video
        exclude_ids = exclude_ids | {video_id}
        
        # Step 1: Quick flat extraction to get candidate video IDs
        mix_url = f"https://www.youtube.com/watch?v={video_id}&list=RD{video_id}"
        
        with yt_dlp.YoutubeDL(YDL_FLAT_OPTS) as ydl:
            try:
                info = ydl.extract_info(mix_url, download=False)
            except yt_dlp.utils.DownloadError as e:
                raise ExtractionError(f"yt-dlp failed: {str(e)}")
        
        if not info:
            raise NotFoundError("Could not get recommendations")
        
        entries = info.get("entries", [])
        if not entries:
            raise NotFoundError("No recommendations found")
        
        # Find first video ID not in exclude list
        next_video_id = None
        for entry in entries:
            if entry and entry.get("id") not in exclude_ids:
                next_video_id = entry.get("id")
                break
        
        if not next_video_id:
            raise NotFoundError("No next track found (all excluded)")
        
        # Step 2: Full extraction for just the selected track
        return self.extract_by_id(next_video_id)

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

