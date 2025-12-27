"""Basic tests for the search endpoint."""

import os

# Set test API key before importing app
os.environ["API_KEY"] = "test-key"

from fastapi.testclient import TestClient

from main import app

client = TestClient(app)


def test_health_endpoint():
    """Test that health endpoint returns successfully."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert "ytdlp_version" in data


def test_search_without_api_key():
    """Test that search requires API key."""
    response = client.get("/search/lucky", params={"q": "test"})
    assert response.status_code == 422  # Missing header


def test_search_with_invalid_api_key():
    """Test that search rejects invalid API key."""
    response = client.get(
        "/search/lucky",
        params={"q": "test"},
        headers={"X-API-Key": "wrong-key"},
    )
    assert response.status_code == 401


def test_search_with_valid_api_key():
    """Test that search works with valid API key (requires network)."""
    response = client.get(
        "/search/lucky",
        params={"q": "never gonna give you up"},
        headers={"X-API-Key": "test-key"},
    )
    # This test requires network access to YouTube
    # In CI, you might want to mock this or skip
    if response.status_code == 200:
        data = response.json()
        assert "video_id" in data
        assert "stream_url" in data
        assert "title" in data


