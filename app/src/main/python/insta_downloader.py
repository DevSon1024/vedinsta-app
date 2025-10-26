import instaloader
import json
import sys
import re
import time
import random
from datetime import datetime

def setup_instaloader():
    """Setup Instaloader with session management"""
    try:
        # Use a more common mobile user agent pattern
        user_agent = 'Mozilla/5.0 (Linux; Android 13; SM-G991U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36 Instagram 298.0.0.34.110 Android (33/13; 450dpi; 1080x2316; samsung; SM-G991U; d1q; qcom; en_US; 308564870)'

        L = instaloader.Instaloader(
            download_video_thumbnails=False,
            download_geotags=False,
            download_comments=False,
            save_metadata=False,
            compress_json=False,
            request_timeout=45, # Slightly increased timeout
            max_connection_attempts=3,
            sleep=True, # Use Instaloader's built-in sleep for rate limits
            quiet=True, # Suppress Instaloader's own console output
            user_agent=user_agent
        )
        # Instaloader handles session loading/saving if configured, no manual login here
        return L
    except Exception as e:
        # Provide error details in the JSON output if setup fails
        print(json.dumps({"status": "error", "message": f"Failed to setup Instaloader: {str(e)[:100]}"}))
        return None # Return None explicitly

def get_media_urls(post_url):
    """
    Fetches media URLs from an Instagram post URL using Instaloader.
    Returns JSON string with status, media details, or error information.
    """
    start_time = time.time()
    try:
        if not post_url or not isinstance(post_url, str):
            return json.dumps({"status": "error", "message": "Invalid URL provided"})

        post_url = post_url.strip()
        if 'instagram.com' not in post_url:
            return json.dumps({"status": "error", "message": "Not a valid Instagram URL"})

        L = setup_instaloader()
        # If setup_instaloader printed JSON error, it would have returned None
        if not L:
             # If setup failed, setup_instaloader already printed the JSON error
             # We exit here to avoid printing another error message.
             # In a production scenario, you might want to return a specific JSON error from here too.
             # For now, rely on the error printed by setup_instaloader.
             sys.exit(1) # Indicate failure

        shortcode = extract_shortcode_from_url(post_url)
        if not shortcode:
            return json.dumps({"status": "error", "message": "Could not extract post ID (shortcode) from URL"})

        post = None
        max_retries = 2
        for attempt in range(max_retries + 1):
            try:
                # Fetch post metadata using the shortcode
                post = instaloader.Post.from_shortcode(L.context, shortcode)
                break # Success, exit retry loop
            except instaloader.exceptions.PrivateProfileNotFollowedException:
                 return json.dumps({"status": "private", "message": "Post is private or account requires following"})
            except instaloader.exceptions.LoginRequiredException:
                 # More specific status for login requirement
                 return json.dumps({"status": "login_required", "message": "Login required to access this post"})
            except instaloader.exceptions.RateLimitException:
                 if attempt < max_retries:
                      wait_time = random.uniform(5, 10) * (attempt + 1) # Exponential backoff
                      print(f"DEBUG: Rate limited. Retrying in {wait_time:.1f} seconds...", file=sys.stderr) # Log retries to stderr
                      time.sleep(wait_time)
                 else:
                      return json.dumps({"status": "rate_limited", "message": "Instagram rate limit exceeded. Please try again later."})
            except instaloader.exceptions.ConnectionException as e:
                 if attempt < max_retries:
                      wait_time = random.uniform(3, 6) * (attempt + 1)
                      print(f"DEBUG: Connection error: {e}. Retrying in {wait_time:.1f} seconds...", file=sys.stderr)
                      time.sleep(wait_time)
                 else:
                      # More specific status for connection errors
                      return json.dumps({"status": "connection_error", "message": f"Connection error after retries: {str(e)[:100]}"})
            except Exception as e: # Catch other potential Instaloader errors more broadly
                error_msg = str(e).lower()
                # Check for common "not found" patterns
                if any(keyword in error_msg for keyword in ['404 not found', 'post not found', 'unavailable', 'media not found']):
                     return json.dumps({"status": "not_found", "message": "Post not found or media is unavailable."})

                # If not a known error, retry or fail
                if attempt < max_retries:
                     wait_time = random.uniform(4, 7) * (attempt + 1)
                     print(f"DEBUG: Unexpected error fetching post: {e}. Retrying in {wait_time:.1f} seconds...", file=sys.stderr)
                     time.sleep(wait_time)
                else:
                    # Generic error status after retries for unknown issues
                    return json.dumps({"status": "error", "message": f"Failed to fetch post after retries: {str(e)[:100]}"})

        # Check if post was successfully fetched after potential retries
        if post is None:
             return json.dumps({"status": "error", "message": "Failed to retrieve post details after multiple attempts."})

        # --- Extract Media Information ---
        media_items = []
        username = post.owner_username or "unknown_user"
        caption_text = post.caption or "" # Default to empty string if no caption

        try:
            if post.is_video:
                # Single video post
                media_url = post.video_url
                if media_url:
                    media_items.append({"url": media_url, "type": "video", "index": 1})
                else:
                    print(f"Warning: Missing video URL for post {shortcode}", file=sys.stderr)
            elif post.typename == "GraphSidecar":
                # Carousel post
                nodes = sorted(list(post.get_sidecar_nodes()), key=lambda node: getattr(node, 'position', 0))
                for i, node in enumerate(nodes):
                    media_url = node.video_url if node.is_video else node.display_url
                    media_type = "video" if node.is_video else "image"
                    if media_url:
                        media_items.append({"url": media_url, "type": media_type, "index": i + 1})
                    else:
                        print(f"Warning: Missing URL for sidecar node {i+1} in post {shortcode}", file=sys.stderr)
            else:
                # Single image post
                media_url = post.url # Instaloader uses 'url' for the main image URL
                if media_url:
                    media_items.append({"url": media_url, "type": "image", "index": 1})
                else:
                    print(f"Warning: Missing image URL for post {shortcode}", file=sys.stderr)

        except Exception as e:
            return json.dumps({"status": "error", "message": f"Failed to extract media details: {str(e)[:100]}"})

        # Check if any valid media URLs were found
        if not media_items:
            # This could happen if URLs were missing or post structure was unexpected
            return json.dumps({"status": "error", "message": "No valid media URLs could be extracted from this post."})

        # --- Prepare Success JSON Output ---
        output_data = {
            "status": "success",
            "username": username,
            "media": media_items,
            "media_count": len(media_items),
            "caption": caption_text,
            # Adding optional metadata if available
            "post_date_utc": post.date_utc.isoformat() if hasattr(post, 'date_utc') else None,
            "likes": getattr(post, 'likes', -1), # Use -1 to indicate not available/fetched
            "comments": getattr(post, 'comments', -1),
            "shortcode": shortcode,
            "extracted_at_utc": datetime.utcnow().isoformat(),
            "processing_time_ms": int((time.time() - start_time) * 1000), # Add processing time
            "post_url": post_url # Keep original post URL for reference
        }
        return json.dumps(output_data, ensure_ascii=False) # ensure_ascii=False for captions with special chars

    except Exception as e:
        # Generic fallback error for unexpected issues during execution
        return json.dumps({
            "status": "error",
            "message": f"Unexpected Python script error: {str(e)[:100]}"
        }, ensure_ascii=False)

def extract_shortcode_from_url(url):
    """Extract shortcode from various Instagram URL patterns"""
    # Consolidated regex to capture shortcode from /p/, /reel/, /reels/, /tv/ paths
    match = re.search(r'instagram\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)', url)
    if match:
        return match.group(1)
    # Add story pattern if needed, though they are harder to handle reliably
    # match = re.search(r'/stories/([^/]+)/([0-9]+)', url)
    # if match: return match.group(2) # Or combine username/id?
    return None

# Keep typo'd function name for backward compatibility if old Android code still calls it
def get_meida_urls(post_url):
    return get_media_urls(post_url)

def main():
    # Expect URL as the first command-line argument
    if len(sys.argv) > 1:
        url = sys.argv[1]
        result_json = get_media_urls(url)
        print(result_json) # Print the resulting JSON to stdout
    else:
        # Provide usage information as JSON if no URL is given
        print(json.dumps({"status": "error", "message": "Usage: python insta_downloader.py <instagram_url>"}))
        sys.exit(1) # Exit with error code

if __name__ == "__main__":
    main()