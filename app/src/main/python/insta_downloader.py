import instaloader
import os
import requests
import re
import json
from datetime import datetime

# --- Configuration (can be simplified as we won't be saving from Python directly) ---
BASE_DIR = "instaloader"
LOG_FILE = os.path.join(BASE_DIR, "downloaded_urls.txt")
ERROR_LOG = os.path.join(BASE_DIR, "error_log.txt")
MAX_QUICK_RETRIES = 2

# --- Helper Functions (mostly unchanged) ---
def log_error(message):
    # In a real app, you might want a better logging mechanism
    print(f"ERROR: {message}")

def setup_instaloader():
    try:
        L = instaloader.Instaloader(
            download_video_thumbnails=False,
            download_geotags=False,
            download_comments=False,
            save_metadata=False,
            request_timeout=15,
            max_connection_attempts=2,
            sleep=False
        )
        return L
    except Exception as e:
        log_error(f"Failed to setup Instaloader: {e}")
        return None

def extract_shortcode_from_url(url):
    patterns = [r'/p/([A-Za-z0-9_-]+)', r'/reel/([A-Za-z0-9_-]+)', r'/tv/([A-Za-z0-9_-]+)']
    for pattern in patterns:
        match = re.search(pattern, url)
        if match:
            return match.group(1)
    return None

def is_likely_restricted_error(error_str):
    restricted_indicators = ["429", "rate limit", "403", "private", "login"]
    return any(indicator in error_str.lower() for indicator in restricted_indicators)

# In app/src/main/python/insta_downloader.py

# --- Main Function for Chaquopy ---
def get_media_urls(post_url):
    """
    This function will be called from Kotlin.
    It takes a post URL and returns a JSON string containing media URLs and the username.
    """
    L = setup_instaloader()
    if not L:
        return json.dumps({"status": "error", "message": "Instaloader init failed."})

    shortcode = extract_shortcode_from_url(post_url)
    if not shortcode:
        return json.dumps({"status": "error", "message": "Could not find post ID in URL."})

    media_items = []

    for attempt in range(MAX_QUICK_RETRIES):
        try:
            post = instaloader.Post.from_shortcode(L.context, shortcode)
            username = post.owner_username  # <-- Get the username

            # Handle carousel posts
            if post.typename == "GraphSidecar":
                for item in post.get_sidecar_nodes():
                    media_url = item.video_url if item.is_video else item.display_url
                    media_type = "video" if item.is_video else "image"
                    media_items.append({"url": media_url, "type": media_type})
            else:
                # Single image or video
                media_url = post.video_url if post.is_video else post.url
                media_type = "video" if post.is_video else "image"
                media_items.append({"url": media_url, "type": media_type})

            # Return username along with media
            return json.dumps({"status": "success", "username": username, "media": media_items})

        except Exception as e:
            error_str = str(e)
            log_error(f"Attempt {attempt + 1}: {error_str}")
            if is_likely_restricted_error(error_str):
                return json.dumps({"status": "private", "message": "Post is private or restricted."})
            if attempt == MAX_QUICK_RETRIES - 1:
                return json.dumps({"status": "error", "message": f"Failed after retries: {error_str[:100]}"})

    return json.dumps({"status": "error", "message": "An unknown error occurred."})