# Codex Implementation Plan (Personal Pixel 5)

## Scope and Assumptions

- Single-user, single-device (Pixel 5), personal use only.
- OK with a few seconds cold start on first play.
- No offline queue/history or playlists for now.
- Private backend; API key can be baked into the app as long as the repo is not shared.

## Goals

- Search a song and start playback quickly.
- Background playback with lock screen controls.
- Large, driving-friendly UI.

## Non-Goals (for now)

- Offline caching, downloads, queue/history.
- Multi-user auth, payments, public distribution.
- Advanced discovery/recommendations.

---

## Phase 0: Decisions and Baseline

- Pin `yt-dlp` version in `requirements.txt` for repeatable behavior.
- Cloud Run min instances = 0 (accept cold starts).
- API key is long random string in app build config; do not publish the repo with it.
- Error envelope is consistent for all endpoints.

**Validation:** A short written checklist of these decisions exists in the repo.

---

## Phase 1: Backend MVP (FastAPI on Cloud Run)

### Deliverables
- `/health` returns status and `yt-dlp` version.
- `/search/lucky` accepts query, returns stream URL + metadata.
- Response schema includes `stream_url`, `expires_at`, `title`, `artist`, `thumbnail`, `duration_ms`, `user_agent`, `skip_segments` (empty list for now).
- Simple API key header check.
- Dockerfile and Cloud Run deployment script.

### Notes
- No Firestore or caching.
- No SponsorBlock in MVP (optional later).
- Avoid runtime `pip install --upgrade yt-dlp` in container; pin versions instead.

**Validation:** `curl` to `/search/lucky` returns a playable stream URL and metadata.

---

## Phase 2: Android Playback Core

### Deliverables
- ExoPlayer + MediaSession + foreground service.
- POST_NOTIFICATIONS permission request for Android 13+.
- Now Playing screen with large play/pause, seek bar, loading state.
- Progress ticker updates UI every 250-500ms while playing.

**Validation:** Hardcoded stream plays with screen off and lock screen controls work.

---

## Phase 3: Integration

### Deliverables
- Retrofit client for `/search/lucky` with API key header.
- Search screen -> fetch track -> start playback with returned `user_agent`.
- Error handling: clear user message for search failure and playback failure.
- Stream expiry handling: if expired, re-run the same search query.

**Validation:** Search -> Play works end-to-end on device within a few seconds.

---

## Phase 4: Optional Enhancements

- SponsorBlock skip segments (backend + SkipHandler).
- Small in-memory cache for repeated searches.
- UI polish for high contrast and large touch targets.
- Keep-screen-awake toggle while playing.

**Validation:** Sponsor segments are skipped, UX is stable while driving.

---

## Risks and Mitigations

- YouTube extraction may break or be blocked; keep usage private and expect maintenance.
- API key is embedded in the app; acceptable for personal use only.

---

## Quick Test Commands

```bash
# Backend local run
cd backend
pip install -r requirements.txt
API_KEY=test uvicorn main:app --reload

# Backend endpoints
curl -H "X-API-Key: test" "http://localhost:8000/health"
curl -H "X-API-Key: test" "http://localhost:8000/search/lucky?q=hotel%20california"
```
