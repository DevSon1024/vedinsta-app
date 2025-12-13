import instaloader
import instaloader.exceptions
from instaloader import ProfileNotExistsException
import json
import sys
import re
import time
import random
from datetime import datetime, timedelta, timezone

def setup_instaloader():
    """Setup Instaloader with session management"""
    try:
        # Updated User Agent to reduce 403 blocks (Samsung S23 Ultra / Android 14)
        user_agent = 'Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.193 Mobile Safari/537.36 Instagram 314.0.0.19.113 Android (34/14; 450dpi; 1440x3088; samsung; SM-S918B; dm3q; qcom; en_US; 557876543)'

        L = instaloader.Instaloader(
            download_video_thumbnails=False,
            download_geotags=False,
            download_comments=False,
            save_metadata=False,
            compress_json=False,
            request_timeout=60, # Increased timeout
            max_connection_attempts=5, # Increased retries
            sleep=True,
            quiet=True,
            user_agent=user_agent
        )
        return L
    except Exception as e:
        print(json.dumps({"status": "error", "message": f"Failed to setup Instaloader: {str(e)[:100]}"}))
        sys.exit(1)


def get_media_urls(url_or_username):
    """
    Fetches media URLs from an Instagram post/reel/story URL or username (for stories).
    Returns JSON string with status, media details, or error information.
    """
    start_time = time.time()
    L = setup_instaloader()

    try:
        url_or_username = url_or_username.strip()
        is_story_request = False
        username_for_stories = None

        story_match = re.search(r'instagram\.com/stories/([^/]+)', url_or_username)
        if story_match:
            is_story_request = True
            username_for_stories = story_match.group(1)
        elif 'instagram.com' not in url_or_username and not re.search(r'[/\\?&=]', url_or_username):
             is_story_request = True
             username_for_stories = url_or_username

        if is_story_request and username_for_stories:
            return fetch_stories(L, username_for_stories, start_time)
        elif 'instagram.com' in url_or_username:
            return fetch_post_or_reel(L, url_or_username, start_time)
        else:
            return json.dumps({"status": "error", "message": "Invalid input: Provide an Instagram URL or username"})

    except Exception as e:
        # Catch-all for unexpected script errors
        return json.dumps({
            "status": "error",
            "message": f"Unexpected Python script error: {str(e)[:100]}"
        }, ensure_ascii=False)


def fetch_stories(L, username, start_time):
    """Fetches story media URLs for a given username."""
    try:
        profile = instaloader.Profile.from_username(L.context, username)
        stories = L.get_stories(userids=[profile.userid])
        media_items = []
        story_items_processed = 0

        for story in stories:
            for item in story.get_items():
                media_url = item.video_url if item.is_video else item.url
                media_type = "video" if item.is_video else "image"

                # Fix Deprecation: use UTC timezone explicitly
                item_date = item.date_utc.replace(tzinfo=timezone.utc).isoformat() if hasattr(item, 'date_utc') else None

                if hasattr(item, 'date_utc') and item.date_utc is not None:
                     item_id = f"{username}_{int(item.date_utc.timestamp())}"
                elif hasattr(item, 'mediaid'):
                     item_id = f"{username}_{item.mediaid}"
                else:
                     item_id = f"{username}_{random.randint(1000,9999)}"

                if media_url:
                    media_items.append({
                        "url": media_url,
                        "type": media_type,
                        "story_item_id": item_id,
                        "date_utc": item_date,
                        "index": story_items_processed + 1
                    })
                    story_items_processed += 1

        if not media_items:
             return json.dumps({
                  "status": "success", "type": "story", "username": username, "media": [],
                  "media_count": 0, "message": "No active stories found."
             })

        output_data = {
            "status": "success", "type": "story", "username": username, "media": media_items,
            "media_count": len(media_items),
            "extracted_at_utc": datetime.now(timezone.utc).isoformat(),
            "processing_time_ms": int((time.time() - start_time) * 1000),
            "post_url": f"https://www.instagram.com/stories/{username}/"
        }
        return json.dumps(output_data, ensure_ascii=False)

    except ProfileNotExistsException:
        return json.dumps({"status": "not_found", "message": f"User '{username}' not found."})
    except instaloader.exceptions.LoginRequiredException:
         return json.dumps({"status": "login_required", "message": "Login required for stories"})
    except instaloader.exceptions.ConnectionException as e:
         return json.dumps({"status": "connection_error", "message": f"Connection failed: {str(e)[:100]}"})
    except Exception as e:
        return json.dumps({"status": "error", "message": f"Story fetch failed: {str(e)[:100]}"})


def fetch_post_or_reel(L, post_url, start_time):
    """Fetches media URLs for a post or reel."""
    shortcode = extract_shortcode_from_url(post_url)
    if not shortcode:
        return json.dumps({"status": "error", "message": "Invalid Instagram URL format"})

    post = None
    max_retries = 3 # Increased retries

    # Retry logic for 403/Connection errors
    for attempt in range(max_retries + 1):
        try:
            post = instaloader.Post.from_shortcode(L.context, shortcode)
            break
        except instaloader.exceptions.LoginRequiredException:
             return json.dumps({"status": "login_required", "message": "Login required for this post"})
        except (instaloader.exceptions.ConnectionException, instaloader.exceptions.RateLimitException) as e:
             if attempt < max_retries:
                  time.sleep(random.uniform(2, 5)) # Wait before retry
                  continue
             return json.dumps({"status": "error", "message": "Connection failed after retries (likely 403 Forbidden)."})
        except Exception as e:
            if "404" in str(e):
                 return json.dumps({"status": "not_found", "message": "Post not found or deleted."})
            return json.dumps({"status": "error", "message": f"Error: {str(e)[:100]}"})

    if post is None:
         return json.dumps({"status": "error", "message": "Failed to load post details."})

    media_items = []
    username = post.owner_username or "unknown"
    caption_text = post.caption or ""

    try:
        if post.is_video:
            if post.video_url:
                media_items.append({"url": post.video_url, "type": "video", "index": 1})
        elif post.typename == "GraphSidecar":
            nodes = sorted([n for n in post.get_sidecar_nodes()], key=lambda n: getattr(n, 'position', 0))
            if not nodes and post.url: # Fallback
                 media_items.append({"url": post.url, "type": "image", "index": 1})
            for i, node in enumerate(nodes):
                url = node.video_url if node.is_video else node.display_url
                m_type = "video" if node.is_video else "image"
                if url: media_items.append({"url": url, "type": m_type, "index": i + 1})
        elif post.url:
            media_items.append({"url": post.url, "type": "image", "index": 1})

    except Exception as e:
        return json.dumps({"status": "error", "message": f"Media extraction failed: {str(e)[:50]}"})

    if not media_items:
        return json.dumps({"status": "error", "message": "No media found in post."})

    output_data = {
        "status": "success", "type": "post", "username": username, "media": media_items,
        "media_count": len(media_items), "caption": caption_text,
        "shortcode": shortcode,
        "extracted_at_utc": datetime.now(timezone.utc).isoformat(),
        "processing_time_ms": int((time.time() - start_time) * 1000),
        "post_url": post_url
    }
    return json.dumps(output_data, ensure_ascii=False)


def extract_shortcode_from_url(url):
    match = re.search(r'instagram\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)', url)
    return match.group(1) if match else None

# Keep legacy function name if used elsewhere
def get_meida_urls(url_or_username):
    return get_media_urls(url_or_username)