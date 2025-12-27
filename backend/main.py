from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from routers import health, search

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

