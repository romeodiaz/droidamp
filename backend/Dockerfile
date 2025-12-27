FROM python:3.12-slim

# Install system dependencies and uv
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg curl && \
    curl -LsSf https://astral.sh/uv/install.sh | sh && \
    rm -rf /var/lib/apt/lists/*

ENV PATH="/root/.local/bin:$PATH"

WORKDIR /app

# Copy dependency files
COPY pyproject.toml .
COPY requirements.txt .

# Install Python dependencies
RUN uv sync --no-dev --frozen

# Copy application code
COPY . .

# Run with pinned yt-dlp version (no runtime upgrades)
CMD ["uv", "run", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]


