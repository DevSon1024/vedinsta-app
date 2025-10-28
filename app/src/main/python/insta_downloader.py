#
# devson1024/vedinsta-app/vedinsta-app-9ec3b010ae1f6ddf1c781f1cbac60602da288453/app/src/main/python/insta_downloader.py
#
import instaloader
# Import the exceptions submodule
import instaloader.exceptions
# Import ProfileNotExistsException directly if it exists at the top level
from instaloader import ProfileNotExistsException
import json
import sys
import re
import time
import random
from datetime import datetime, timedelta

def setup_instaloader():
    """Setup Instaloader with session management"""
    try:
        user_agent = 'Mozilla/5.0 (Linux; Android 13; SM-G991U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36 Instagram 298.0.0.34.110 Android (33/13; 450dpi; 1080x2316; samsung; SM-G991U; d1q; qcom; en_US; 308564870)'
        L = instaloader.Instaloader(
            download_video_thumbnails=False,
            download_geotags=False,
            download_comments=False,
            save_metadata=False,
            compress_json=False,
            request_timeout=45,
            max_connection_attempts=3,
            sleep=True,
            quiet=True,
            user_agent=user_agent
        )
        # Session loading attempt (optional)
        # try:
        #     L.load_session_from_file('your_username')
        # except FileNotFoundError:
        #     print("DEBUG: Session file not found.", file=sys.stderr)
        # except Exception as e:
        #     print(f"DEBUG: Error loading session: {e}", file=sys.stderr)
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
            print(f"DEBUG: Detected story request for user: {username_for_stories}", file=sys.stderr)
        elif 'instagram.com' not in url_or_username and not re.search(r'[/\\?&=]', url_or_username):
             is_story_request = True
             username_for_stories = url_or_username
             print(f"DEBUG: Assuming username provided for story request: {username_for_stories}", file=sys.stderr)

        if is_story_request and username_for_stories:
            return fetch_stories(L, username_for_stories, start_time)
        elif 'instagram.com' in url_or_username:
            return fetch_post_or_reel(L, url_or_username, start_time)
        else:
            return json.dumps({"status": "error", "message": "Invalid input: Provide an Instagram URL or username"})

    except Exception as e:
        print(f"ERROR: Unexpected error in get_media_urls: {e}", file=sys.stderr)
        return json.dumps({
            "status": "error",
            "message": f"Unexpected Python script error: {str(e)[:100]}"
        }, ensure_ascii=False)


def fetch_stories(L, username, start_time):
    """Fetches story media URLs for a given username."""
    print(f"DEBUG: Attempting to fetch stories for {username}", file=sys.stderr)
    media_items = []
    profile = None
    try:
        profile = instaloader.Profile.from_username(L.context, username)

        stories = L.get_stories(userids=[profile.userid])
        story_items_processed = 0

        for story in stories:
            for item in story.get_items():
                media_url = item.video_url if item.is_video else item.url
                media_type = "video" if item.is_video else "image"
                item_date_utc_iso = item.date_utc.isoformat() if hasattr(item, 'date_utc') else None
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
                        "date_utc": item_date_utc_iso,
                        "index": story_items_processed + 1
                    })
                    story_items_processed += 1
                else:
                    print(f"Warning: Missing URL for story item from {username}", file=sys.stderr)

        if not media_items:
             return json.dumps({
                  "status": "success", "type": "story", "username": username, "media": [],
                  "media_count": 0, "caption": None, "message": "No active stories found for this user.",
                  "processing_time_ms": int((time.time() - start_time) * 1000)
             })

        print(f"DEBUG: Found {len(media_items)} story items for {username}", file=sys.stderr)
        output_data = {
            "status": "success", "type": "story", "username": username, "media": media_items,
            "media_count": len(media_items), "caption": None,
            "extracted_at_utc": datetime.utcnow().isoformat(),
            "processing_time_ms": int((time.time() - start_time) * 1000),
            "post_url": f"https://www.instagram.com/stories/{username}/"
        }
        return json.dumps(output_data, ensure_ascii=False)

    except ProfileNotExistsException: # Imported directly
        print(f"DEBUG: ProfileNotExistsException caught for user {username}", file=sys.stderr)
        return json.dumps({"status": "not_found", "message": f"User '{username}' not found."})
    # Use exceptions via the submodule
    except instaloader.exceptions.PrivateProfileNotFollowedException:
         print(f"DEBUG: PrivateProfileNotFollowedException caught for user {username}", file=sys.stderr)
         return json.dumps({"status": "private", "message": "Cannot fetch stories: Profile is private or requires following"})
    except instaloader.exceptions.LoginRequiredException:
         print(f"DEBUG: LoginRequiredException caught for user {username}", file=sys.stderr)
         return json.dumps({"status": "login_required", "message": "Login required to access stories"})
    except instaloader.exceptions.RateLimitException:
         print(f"DEBUG: RateLimitException caught for user {username}", file=sys.stderr)
         return json.dumps({"status": "rate_limited", "message": "Instagram rate limit exceeded fetching stories. Try later."})
    except instaloader.exceptions.ConnectionException as e:
         print(f"DEBUG: ConnectionException caught for user {username}: {e}", file=sys.stderr)
         return json.dumps({"status": "connection_error", "message": f"Connection error fetching stories: {str(e)[:100]}"})
    except Exception as e:
        import traceback
        print(f"ERROR: Unexpected error in fetch_stories for {username}:\n{traceback.format_exc()}", file=sys.stderr)
        return json.dumps({"status": "error", "message": f"Failed to fetch stories for {username}: {str(e)[:100]}"})


def fetch_post_or_reel(L, post_url, start_time):
    """Fetches media URLs for a post or reel."""
    print(f"DEBUG: Attempting to fetch post/reel: {post_url}", file=sys.stderr)
    shortcode = extract_shortcode_from_url(post_url)
    if not shortcode:
        return json.dumps({"status": "error", "message": "Could not extract post ID (shortcode) from URL"})

    post = None
    max_retries = 2
    for attempt in range(max_retries + 1):
        try:
            post = instaloader.Post.from_shortcode(L.context, shortcode)
            break
        # Use exceptions via the submodule
        except instaloader.exceptions.PrivateProfileNotFollowedException:
             return json.dumps({"status": "private", "message": "Post is private or account requires following"})
        except instaloader.exceptions.LoginRequiredException:
             return json.dumps({"status": "login_required", "message": "Login required to access this post"})
        except instaloader.exceptions.RateLimitException:
             if attempt < max_retries:
                  wait_time = random.uniform(5, 10) * (attempt + 1)
                  print(f"DEBUG: Rate limited (Post). Retrying in {wait_time:.1f}s...", file=sys.stderr)
                  time.sleep(wait_time)
             else:
                  return json.dumps({"status": "rate_limited", "message": "Instagram rate limit exceeded. Try later."})
        except instaloader.exceptions.ConnectionException as e:
             if attempt < max_retries:
                  wait_time = random.uniform(3, 6) * (attempt + 1)
                  print(f"DEBUG: Connection error (Post): {e}. Retrying in {wait_time:.1f}s...", file=sys.stderr)
                  time.sleep(wait_time)
             else:
                  return json.dumps({"status": "connection_error", "message": f"Connection error after retries: {str(e)[:100]}"})
        except Exception as e:
            error_msg = str(e).lower()
            if any(keyword in error_msg for keyword in ['404 not found', 'post not found', 'unavailable', 'media not found', 'post is unavailable']):
                 print(f"DEBUG: Post/Media not found pattern matched: {error_msg}", file=sys.stderr)
                 return json.dumps({"status": "not_found", "message": "Post not found or media is unavailable."})
            if attempt < max_retries:
                 wait_time = random.uniform(4, 7) * (attempt + 1)
                 print(f"DEBUG: Unexpected error (Post): {e}. Retrying in {wait_time:.1f}s...", file=sys.stderr)
                 time.sleep(wait_time)
            else:
                import traceback
                print(f"ERROR: Failed to fetch post {shortcode} after retries:\n{traceback.format_exc()}", file=sys.stderr)
                return json.dumps({"status": "error", "message": f"Failed to fetch post after retries: {str(e)[:100]}"})

    if post is None:
         print(f"ERROR: Post object is None for {shortcode} after retry loop.", file=sys.stderr)
         return json.dumps({"status": "error", "message": "Failed to retrieve post details after multiple attempts."})

    # --- Extract Media Information ---
    media_items = []
    username = post.owner_username or "unknown_user"
    caption_text = post.caption or ""

    try:
        if post.is_video:
            media_url = post.video_url
            if media_url:
                media_items.append({"url": media_url, "type": "video", "index": 1})
            else:
                print(f"Warning: Missing video URL for post {shortcode}", file=sys.stderr)
        elif post.typename == "GraphSidecar":
            nodes_iterable = post.get_sidecar_nodes()
            nodes = []
            if nodes_iterable:
                 nodes = sorted(list(nodes_iterable), key=lambda node: getattr(node, 'position', 0))

            if not nodes:
                 print(f"Warning: Could not retrieve sidecar nodes for post {shortcode}. Trying main post URL.", file=sys.stderr)
                 media_url = post.url
                 if media_url:
                      media_items.append({"url": media_url, "type": "image", "index": 1})
                 else:
                      print(f"ERROR: Missing main URL for post {shortcode} after sidecar failure.", file=sys.stderr)
            else:
                 for i, node in enumerate(nodes):
                      media_url = node.video_url if node.is_video else node.display_url
                      media_type = "video" if node.is_video else "image"
                      if media_url:
                           media_items.append({"url": media_url, "type": media_type, "index": i + 1})
                      else:
                           print(f"Warning: Missing URL for sidecar node {i+1} in post {shortcode}", file=sys.stderr)
        else:
            media_url = post.url
            if media_url:
                media_items.append({"url": media_url, "type": "image", "index": 1})
            else:
                print(f"Warning: Missing image URL for post {shortcode}", file=sys.stderr)

    except Exception as e:
        import traceback
        print(f"ERROR: Failed to extract media details for {shortcode}:\n{traceback.format_exc()}", file=sys.stderr)
        return json.dumps({"status": "error", "message": f"Failed to extract media details: {str(e)[:100]}"})

    if not media_items:
        print(f"ERROR: No media items extracted for post {shortcode} despite successful fetch.", file=sys.stderr)
        return json.dumps({"status": "error", "message": "No valid media URLs could be extracted from this post."})

    # --- Prepare Success JSON Output ---
    output_data = {
        "status": "success", "type": "post", "username": username, "media": media_items,
        "media_count": len(media_items), "caption": caption_text,
        "post_date_utc": post.date_utc.isoformat() if hasattr(post, 'date_utc') else None,
        "likes": getattr(post, 'likes', -1), "comments": getattr(post, 'comments', -1),
        "shortcode": shortcode, "extracted_at_utc": datetime.utcnow().isoformat(),
        "processing_time_ms": int((time.time() - start_time) * 1000), "post_url": post_url
    }
    return json.dumps(output_data, ensure_ascii=False)


def extract_shortcode_from_url(url):
    """Extract shortcode from various Instagram URL patterns"""
    match = re.search(r'instagram\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)', url)
    if match:
        return match.group(1)
    return None

# Keep typo'd function name
def get_meida_urls(url_or_username):
    return get_media_urls(url_or_username)

def main():
    if len(sys.argv) > 1:
        input_arg = sys.argv[1]
        try:
            result_json = get_media_urls(input_arg)
            print(result_json)
        except Exception as e:
            import traceback
            print(f"FATAL ERROR in main execution:\n{traceback.format_exc()}", file=sys.stderr)
            print(json.dumps({"status": "error", "message": f"Fatal script error: {str(e)[:100]}"}))
            sys.exit(1)
    else:
        print(json.dumps({"status": "error", "message": "Usage: python insta_downloader.py <instagram_url_or_username>"}))
        sys.exit(1)

if __name__ == "__main__":
    main()