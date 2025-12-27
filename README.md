# DroidAmp

A music streaming Android app with a Python/FastAPI backend.

## Project Structure

```
droidamp/
├── android/          # Android app (Kotlin, Jetpack Compose)
├── backend/          # FastAPI server (Python)
```

## Backend

### Prerequisites

- [uv](https://docs.astral.sh/uv/) (Python package manager)
- Python 3.12+ (uv will install it automatically)
- ffmpeg (for yt-dlp audio extraction)

### Setup

```bash
cd backend

# Install dependencies (uv creates .venv automatically)
uv sync

# Or with dev dependencies (pytest)
uv sync --dev
```

### Configuration

Create a `.env` file in the `backend/` directory:

```env
API_KEY=your-secret-api-key
```

Or set the environment variable directly:

```bash
export API_KEY=your-secret-api-key
```

### Running the Server

```bash
cd backend

# Development (with hot reload)
uv run uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

The server will be available at `http://127.0.0.1:8000`

### Running Tests

```bash
cd backend

# Run all tests
uv run pytest tests/ -v

# Run with coverage
uv run pytest tests/ -v --cov=.
```

### Docker

```bash
cd backend

# Build
docker build -t droidamp-backend .

# Run
docker run -p 8080:8080 -e API_KEY=your-secret-api-key droidamp-backend
```

## Android App

### Prerequisites

- Android Studio (latest stable)
- JDK 17+
- Android SDK 34+

### Setup

1. Open `android/` folder in Android Studio
2. Sync Gradle files
3. Update `local.properties` if needed

### Running

- Connect a device or start an emulator
- Click **Run** (or `./gradlew installDebug` from terminal)

### Building

```bash
cd android

# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease
```

APK output: `android/app/build/outputs/apk/`

### Linting & Checks

```bash
cd android

# Run lint
./gradlew lint

# Run all checks
./gradlew check
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/search/lucky?q=<query>` | GET | Search and get stream URL (requires `X-API-Key` header) |

## Development Tips

- Backend uses yt-dlp for YouTube audio extraction
- Stream URLs expire after ~6 hours (cached for 3 hours)
- Android app expects backend at `127.0.0.1:8000` for local dev (update in `ApiClient.kt` for production)
