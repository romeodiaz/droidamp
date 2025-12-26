# DroidAmp Implementation Plan

## Overview

A personal music streaming app that searches YouTube for audio, extracts direct stream URLs via a serverless backend, and plays them on an Android device.

**Architecture:** Serverless Python backend (Cloud Run) + Native Android client (Kotlin/Compose)

---

## Scope and Assumptions

- Single-user, single-device (Pixel 5), personal use only
- OK with a few seconds cold start on first play
- No offline queue/history or playlists for now
- Private backend; API key can be baked into the app as long as the repo is not shared

## Goals

- Search a song and start playback quickly
- Background playback with lock screen controls
- Large, driving-friendly UI

## Non-Goals (for now)

- Offline caching, downloads, queue/history
- Multi-user auth, payments, public distribution
- Advanced discovery/recommendations

## Risks and Mitigations

- **YouTube extraction may break or be blocked** - Keep usage private and expect maintenance; pin yt-dlp version for reproducibility
- **API key is embedded in the app** - Acceptable for personal use only; do not publish repo

---

## Part 1: Backend Service

### 1.1 Project Structure

```
backend/
├── Dockerfile
├── requirements.txt
├── main.py                 # FastAPI app entry point
├── config.py               # Environment variables & settings
├── routers/
│   ├── __init__.py
│   ├── search.py           # /search endpoints
│   └── health.py           # /health endpoint
├── services/
│   ├── __init__.py
│   ├── youtube.py          # yt-dlp wrapper
│   └── cache.py            # In-memory caching layer
├── models/
│   ├── __init__.py
│   └── schemas.py          # Pydantic models
└── tests/
    └── test_search.py
```

### 1.2 Dependencies

```txt
# requirements.txt
fastapi==0.109.0
uvicorn[standard]==0.27.0
yt-dlp==2024.1.1
httpx==0.26.0
pydantic==2.5.3
pydantic-settings==2.1.0
```

### 1.3 Configuration

```python
# config.py
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
```

### 1.4 Data Models

```python
# models/schemas.py
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
```

### 1.5 YouTube Service (yt-dlp Wrapper)

```python
# services/youtube.py
import yt_dlp
import time
from models.schemas import TrackResponse
from config import settings

# Reusable yt-dlp options
YDL_OPTS = {
    "format": "bestaudio[acodec=opus]/bestaudio",  # Prefer Opus
    "quiet": True,
    "no_warnings": True,
    "extract_flat": False,
    "geo_bypass": True,
    "socket_timeout": 10,
    # Spoof as Android app for better compatibility
    "http_headers": {
        "User-Agent": settings.user_agent,
    },
}

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

        if not info or "entries" not in info or not info["entries"]:
            raise NotFoundError("No results found")

        entry = info["entries"][0]
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
        audio_formats = [f for f in formats if f.get("acodec") != "none" and f.get("vcodec") == "none"]

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
        artist = entry.get("artist") or entry.get("uploader") or self._extract_artist(title)

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
        import re
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


class ExtractionError(Exception):
    pass

class NotFoundError(Exception):
    pass
```

### 1.6 In-Memory Cache

```python
# services/cache.py
import time
import hashlib
from typing import Dict, Tuple
from models.schemas import TrackResponse
from config import settings

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
            k for k, (resp, cached_at) in self._cache.items()
            if now - cached_at > settings.cache_ttl_seconds
            or resp.expires_at < now + 1800
        ]
        for k in expired_keys:
            del self._cache[k]
```

### 1.7 API Routes

```python
# routers/search.py
from fastapi import APIRouter, HTTPException, Header, Query
from models.schemas import TrackResponse, ErrorResponse
from services.youtube import YouTubeService, ExtractionError, NotFoundError
from services.cache import CacheService
from config import settings

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
        raise HTTPException(status_code=404, detail=ErrorResponse(
            error="No results found",
            code="NOT_FOUND",
        ).model_dump())
    except ExtractionError as e:
        raise HTTPException(status_code=502, detail=ErrorResponse(
            error=str(e),
            code="EXTRACTION_FAILED",
            retry_after=60,
        ).model_dump())

    # Cache the result
    cache.set(q, track)

    return track
```

```python
# routers/health.py
from fastapi import APIRouter
import subprocess
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
```

### 1.8 Main Application

```python
# main.py
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from routers import search, health

app = FastAPI(
    title="DroidAmp API",
    version="1.0.0",
    docs_url=None,  # Disable Swagger in production
    redoc_url=None,
)

# CORS (restrict in production)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Lock down for production
    allow_methods=["GET"],
    allow_headers=["X-API-Key"],
)

app.include_router(health.router)
app.include_router(search.router)


@app.on_event("startup")
async def startup():
    # Warm up yt-dlp by importing extractors
    import yt_dlp
    yt_dlp.YoutubeDL()
```

### 1.9 Dockerfile

```dockerfile
FROM python:3.11-slim

# Install system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Run with pinned yt-dlp version (no runtime upgrades)
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]
```

### 1.10 Cloud Run Deployment

```yaml
# cloudbuild.yaml
steps:
  - name: 'gcr.io/cloud-builders/docker'
    args: ['build', '-t', 'gcr.io/$PROJECT_ID/droidamp-api', './backend']

  - name: 'gcr.io/cloud-builders/docker'
    args: ['push', 'gcr.io/$PROJECT_ID/droidamp-api']

  - name: 'gcr.io/cloud-builders/gcloud'
    args:
      - 'run'
      - 'deploy'
      - 'droidamp-api'
      - '--image=gcr.io/$PROJECT_ID/droidamp-api'
      - '--platform=managed'
      - '--region=us-central1'
      - '--memory=1Gi'
      - '--cpu=1'
      - '--concurrency=15'
      - '--timeout=60s'
      - '--min-instances=0'
      - '--max-instances=3'
      - '--set-env-vars=API_KEY=${_API_KEY}'
      - '--allow-unauthenticated'  # Auth handled at app level
```

**Deploy command:**
```bash
gcloud builds submit --config=cloudbuild.yaml --substitutions=_API_KEY="your-secret-key"
```

---

## Part 2: Android Client

### 2.1 Project Structure

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/droidamp/
│   │   ├── DroidAmpApplication.kt
│   │   ├── MainActivity.kt
│   │   ├── ui/
│   │   │   ├── theme/
│   │   │   │   ├── Theme.kt
│   │   │   │   └── Color.kt
│   │   │   ├── screens/
│   │   │   │   ├── NowPlayingScreen.kt
│   │   │   │   └── SearchScreen.kt
│   │   │   └── components/
│   │   │       ├── PlayerControls.kt
│   │   │       ├── TrackInfo.kt
│   │   │       └── ProgressBar.kt
│   │   ├── player/
│   │   │   ├── AudioService.kt        # Foreground service
│   │   │   ├── PlayerManager.kt       # ExoPlayer wrapper
│   │   │   └── SkipHandler.kt         # SponsorBlock logic (Phase 4)
│   │   ├── data/
│   │   │   ├── api/
│   │   │   │   ├── DroidAmpApi.kt     # Retrofit interface
│   │   │   │   └── ApiClient.kt       # HTTP client setup
│   │   │   ├── models/
│   │   │   │   └── Track.kt
│   │   │   └── repository/
│   │   │       └── TrackRepository.kt
│   │   └── viewmodel/
│   │       └── PlayerViewModel.kt
│   └── res/
│       ├── values/
│       │   └── strings.xml
│       └── drawable/
│           └── ic_notification.xml
```

### 2.2 Dependencies

```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.droidamp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.droidamp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Store API config in BuildConfig
        buildConfigField("String", "API_BASE_URL", "\"https://droidamp-api-xxxxx.run.app\"")
        buildConfigField("String", "API_KEY", "\"your-api-key\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Media3 (ExoPlayer)
    val media3Version = "1.2.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
}
```

### 2.3 AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".DroidAmpApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.DroidAmp">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".player.AudioService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

### 2.4 Data Models

```kotlin
// data/models/Track.kt
package com.droidamp.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrackResponse(
    @SerialName("video_id") val videoId: String,
    @SerialName("stream_url") val streamUrl: String,
    @SerialName("expires_at") val expiresAt: Long,
    val title: String,
    val artist: String?,
    val thumbnail: String,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("skip_segments") val skipSegments: List<SkipSegment>,
    @SerialName("user_agent") val userAgent: String,
)

@Serializable
data class SkipSegment(
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val category: String,
)

// Local track model with additional state
data class Track(
    val videoId: String,
    val streamUrl: String,
    val expiresAt: Long,
    val title: String,
    val artist: String?,
    val thumbnail: String,
    val durationMs: Long,
    val skipSegments: List<SkipSegment>,
    val userAgent: String,
    val originalQuery: String,  // Store for refresh
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() / 1000 > expiresAt - 1800 // 30min buffer

    companion object {
        fun from(response: TrackResponse, query: String) = Track(
            videoId = response.videoId,
            streamUrl = response.streamUrl,
            expiresAt = response.expiresAt,
            title = response.title,
            artist = response.artist,
            thumbnail = response.thumbnail,
            durationMs = response.durationMs,
            skipSegments = response.skipSegments,
            userAgent = response.userAgent,
            originalQuery = query,
        )
    }
}
```

### 2.5 API Client

```kotlin
// data/api/DroidAmpApi.kt
package com.droidamp.data.api

import com.droidamp.data.models.TrackResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DroidAmpApi {
    @GET("/search/lucky")
    suspend fun searchLucky(
        @Query("q") query: String,
        @Header("X-API-Key") apiKey: String,
    ): TrackResponse
}
```

```kotlin
// data/api/ApiClient.kt
package com.droidamp.data.api

import com.droidamp.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: DroidAmpApi = retrofit.create(DroidAmpApi::class.java)
}
```

### 2.6 Repository

```kotlin
// data/repository/TrackRepository.kt
package com.droidamp.data.repository

import com.droidamp.BuildConfig
import com.droidamp.data.api.ApiClient
import com.droidamp.data.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrackRepository {
    private val api = ApiClient.api

    suspend fun search(query: String): Result<Track> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchLucky(query, BuildConfig.API_KEY)
            Result.success(Track.from(response, query))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refresh an expired track by re-running the original search query.
     * This is simpler than a dedicated refresh endpoint and works well
     * since the backend caches by query.
     */
    suspend fun refreshTrack(track: Track): Result<Track> = withContext(Dispatchers.IO) {
        search(track.originalQuery)
    }
}
```

### 2.7 ExoPlayer Manager

```kotlin
// player/PlayerManager.kt
package com.droidamp.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.droidamp.data.models.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Handler
import android.os.Looper

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class PlayerManager(context: Context) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentUserAgent: String? = null

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    isLoading = playbackState == Player.STATE_BUFFERING,
                    error = if (playbackState == Player.STATE_IDLE) "Playback stopped" else null,
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
        })
    }

    private var skipHandler: SkipHandler? = null

    // Progress ticker: update UI every 250-500ms while playing
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (exoPlayer.isPlaying) {
                _state.value = _state.value.copy(positionMs = exoPlayer.currentPosition)
            }
            progressHandler.postDelayed(this, 250) // Update every 250ms
        }
    }

    init {
        progressHandler.post(progressRunnable)
    }

    fun play(track: Track) {
        // Create data source factory with track-specific User-Agent
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(track.userAgent)
            .setDefaultRequestProperties(mapOf("Referer" to "https://youtube.com"))

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(track.streamUrl))

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()

        _state.value = _state.value.copy(
            currentTrack = track,
            durationMs = track.durationMs,
            positionMs = 0,
            isLoading = true,
        )

        // Setup skip handler for SponsorBlock segments (Phase 4)
        skipHandler?.stop()
        if (track.skipSegments.isNotEmpty()) {
            skipHandler = SkipHandler(exoPlayer, track.skipSegments).also { it.start() }
        }

        currentUserAgent = track.userAgent
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    fun release() {
        progressHandler.removeCallbacks(progressRunnable)
        skipHandler?.stop()
        exoPlayer.release()
    }
}
```

### 2.8 SponsorBlock Skip Handler (Phase 4)

```kotlin
// player/SkipHandler.kt
package com.droidamp.player

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import com.droidamp.data.models.SkipSegment

/**
 * Handles automatic skipping of SponsorBlock segments during playback.
 * Note: This is implemented in Phase 4 when SponsorBlock is enabled on backend.
 */
class SkipHandler(
    private val player: ExoPlayer,
    private val segments: List<SkipSegment>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isActive = false

    // Track which segments we've already skipped to avoid loops
    private val skippedSegments = mutableSetOf<Int>()

    fun start() {
        if (segments.isEmpty()) return
        isActive = true
        scheduleNextCheck()
    }

    fun stop() {
        isActive = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun scheduleNextCheck() {
        if (!isActive) return

        val currentPos = player.currentPosition
        val nextSegment = findNextSegment(currentPos)

        if (nextSegment != null) {
            val (index, segment) = nextSegment
            val delay = (segment.startMs - currentPos).coerceAtLeast(0)

            handler.postDelayed({
                if (isActive && !skippedSegments.contains(index)) {
                    performSkip(index, segment)
                }
                scheduleNextCheck()
            }, delay.coerceAtMost(1000)) // Check at least every second
        } else {
            // No more segments, check again in 2 seconds
            handler.postDelayed({ scheduleNextCheck() }, 2000)
        }
    }

    private fun findNextSegment(currentPos: Long): Pair<Int, SkipSegment>? {
        return segments.withIndex()
            .filter { (index, seg) ->
                !skippedSegments.contains(index) &&
                currentPos < seg.endMs &&
                currentPos >= seg.startMs - 500 // Within 500ms of start
            }
            .minByOrNull { (_, seg) -> seg.startMs }
            ?.let { (index, seg) -> index to seg }
            ?: segments.withIndex()
                .filter { (index, seg) ->
                    !skippedSegments.contains(index) &&
                    currentPos < seg.startMs
                }
                .minByOrNull { (_, seg) -> seg.startMs }
                ?.let { (index, seg) -> index to seg }
    }

    private fun performSkip(index: Int, segment: SkipSegment) {
        val currentPos = player.currentPosition

        // Only skip if we're actually in the segment
        if (currentPos >= segment.startMs - 100 && currentPos < segment.endMs) {
            player.seekTo(segment.endMs)
            skippedSegments.add(index)
        }
    }
}
```

### 2.9 Audio Service (Foreground Service)

```kotlin
// player/AudioService.kt
package com.droidamp.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.droidamp.DroidAmpApplication
import com.droidamp.MainActivity

class AudioService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var playerManager: PlayerManager

    override fun onCreate() {
        super.onCreate()

        playerManager = (application as DroidAmpApplication).playerManager

        // Create pending intent for notification tap
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, playerManager.exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
```

### 2.10 ViewModel

```kotlin
// viewmodel/PlayerViewModel.kt
package com.droidamp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidamp.data.models.Track
import com.droidamp.data.repository.TrackRepository
import com.droidamp.player.PlayerManager
import com.droidamp.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val playerState: PlayerState = PlayerState(),
)

class PlayerViewModel(
    private val playerManager: PlayerManager,
    private val repository: TrackRepository = TrackRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Collect player state updates
        viewModelScope.launch {
            playerManager.state.collect { playerState ->
                _uiState.value = _uiState.value.copy(playerState = playerState)

                // Auto-refresh expired streams by re-running original search
                playerState.currentTrack?.let { track ->
                    if (track.isExpired) {
                        refreshCurrentTrack(track)
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, searchError = null)

            repository.search(query)
                .onSuccess { track ->
                    _uiState.value = _uiState.value.copy(isSearching = false)
                    playerManager.play(track)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchError = error.message ?: "Search failed"
                    )
                }
        }
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    private fun refreshCurrentTrack(track: Track) {
        viewModelScope.launch {
            repository.refreshTrack(track)
                .onSuccess { refreshedTrack ->
                    playerManager.play(refreshedTrack)
                }
                .onFailure {
                    // Stream expired and refresh failed - notify user
                    _uiState.value = _uiState.value.copy(
                        searchError = "Stream expired. Please search again."
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(searchError = null)
    }
}
```

### 2.11 Main UI Screens

```kotlin
// ui/screens/NowPlayingScreen.kt
package com.droidamp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.droidamp.ui.components.PlayerControls
import com.droidamp.ui.components.ProgressBar
import com.droidamp.ui.theme.AmpGreen
import com.droidamp.viewmodel.PlayerViewModel

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val track = uiState.playerState.currentTrack

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Search button at top
        OutlinedButton(
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AmpGreen
            )
        ) {
            Text("Search", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Album art
        if (track != null) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = "Album art",
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A4E)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No Track",
                    color = Color.Gray,
                    fontSize = 20.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Track info
        Text(
            text = track?.title ?: "—",
            color = AmpGreen,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track?.artist ?: "",
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Progress bar
        ProgressBar(
            positionMs = uiState.playerState.positionMs,
            durationMs = uiState.playerState.durationMs,
            onSeek = { viewModel.seekTo(it) },
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Player controls (large, driver-friendly)
        PlayerControls(
            isPlaying = uiState.playerState.isPlaying,
            isLoading = uiState.playerState.isLoading,
            onPlayPause = { viewModel.togglePlayPause() },
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Error display
        uiState.searchError?.let { error ->
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}
```

```kotlin
// ui/screens/SearchScreen.kt
package com.droidamp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidamp.ui.theme.AmpGreen
import com.droidamp.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(16.dp),
    ) {
        // Back button
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = AmpGreen,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search input
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Song name or artist...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmpGreen,
                cursorColor = AmpGreen,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.search()
                    onBack()
                }
            ),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Search button (large, driver-friendly)
        Button(
            onClick = {
                viewModel.search()
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = uiState.searchQuery.isNotBlank() && !uiState.isSearching,
            colors = ButtonDefaults.buttonColors(
                containerColor = AmpGreen,
                contentColor = Color.Black,
            )
        ) {
            if (uiState.isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.Black,
                )
            } else {
                Text("Play", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hint text
        Text(
            text = "Tip: Add 'lyrics' or 'acoustic' to find specific versions",
            color = Color.Gray,
            fontSize = 14.sp,
        )
    }
}
```

### 2.12 Theme

```kotlin
// ui/theme/Color.kt
package com.droidamp.ui.theme

import androidx.compose.ui.graphics.Color

val AmpGreen = Color(0xFF00FF00)
val AmpDark = Color(0xFF1A1A2E)
val AmpDarker = Color(0xFF0F0F1A)
```

```kotlin
// ui/theme/Theme.kt
package com.droidamp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AmpGreen,
    secondary = AmpGreen,
    background = AmpDark,
    surface = AmpDarker,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun DroidAmpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
```

---

## Part 3: Implementation Phases

### Phase 1: Backend MVP
- [ ] Set up Python project structure
- [ ] Implement `/health` endpoint
- [ ] Implement `/search/lucky` endpoint (with in-memory cache)
- [ ] Write Dockerfile (with pinned yt-dlp version)
- [ ] Deploy to Cloud Run
- [ ] Test with curl from laptop

**Validation:** `curl -H "X-API-Key: xxx" "https://your-url/search/lucky?q=test"` returns valid JSON with stream URL

### Phase 2: Android Audio Core
- [ ] Create Android project with dependencies
- [ ] Implement PlayerManager with ExoPlayer
- [ ] Implement progress ticker (250ms updates)
- [ ] Implement AudioService for background playback
- [ ] Request POST_NOTIFICATIONS permission for Android 13+
- [ ] Test with hardcoded stream URL
- [ ] Verify lock screen controls work

**Validation:** App plays audio and continues when screen is off

### Phase 3: Integration
- [ ] Implement API client and repository
- [ ] Connect ViewModel to PlayerManager
- [ ] Build basic Now Playing screen
- [ ] Build Search screen (large touch targets)
- [ ] Implement stream expiry refresh (re-run search)
- [ ] Test end-to-end flow

**Validation:** Search -> Play works on device; expired streams auto-refresh

### Phase 4: Optional Enhancements
- [ ] Add SponsorBlock service to backend
- [ ] Integrate SponsorBlock in `/search/lucky` response
- [ ] Enable SkipHandler on Android
- [ ] Add error handling polish
- [ ] Add loading states
- [ ] Add keep-screen-awake toggle

**Validation:** Sponsor segments are skipped; UX is stable while driving

### Phase 5: Polish
- [ ] Refine UI for high contrast and large touch targets
- [ ] Add queue management (Room DB)
- [ ] Add play history
- [ ] Performance optimization

---

## Appendix: Testing Commands

**Backend local testing:**
```bash
cd backend
pip install -r requirements.txt
API_KEY=test uvicorn main:app --reload

# Test endpoints
curl -H "X-API-Key: test" "http://localhost:8000/health"
curl -H "X-API-Key: test" "http://localhost:8000/search/lucky?q=hotel%20california"
```

**Stream URL validation:**
```bash
# Test that stream URL is playable
ffplay -nodisp -autoexit "STREAM_URL_HERE"
```

**Android build:**
```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

