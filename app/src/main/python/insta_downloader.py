import instaloader
import json
import sys
import re
import time
import random
from datetime import datetime
# Removed unused urllib imports

def setup_instaloader():
    """Setup Instaloader with session management"""
    try:
        L = instaloader.Instaloader(
            download_video_thumbnails=False,
            download_geotags=False,
            download_comments=False,
            save_metadata=False,
            compress_json=False,
            request_timeout=45, # Increased timeout slightly
            max_connection_attempts=3,
            sleep=True, # Use Instaloader's built-in sleep
            quiet=True, # Suppress Instaloader's own console output
            user_agent='Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 Instagram 308.0.0.34.113 Android (31/12; 450dpi; 1080x2392; samsung; SM-G991B; o1s; exynos2100; en_US; 458229237)'
        )
        # No need to manually update headers if using Instaloader's mechanisms
        return L
    except Exception as e:
        print(f"ERROR: Failed to setup Instaloader: {e}")
        return None

def get_media_urls(post_url):
    """
    Simplified main function relying on Instaloader for URL fetching.
    """
    try:
        if not post_url or not isinstance(post_url, str):
            return json.dumps({"status": "error", "message": "Invalid URL provided"})

        post_url = post_url.strip()
        if 'instagram.com' not in post_url:
            return json.dumps({"status": "error", "message": "Not a valid Instagram URL"})

        L = setup_instaloader()
        if not L:
            return json.dumps({"status": "error", "message": "Failed to initialize Instagram loader"})

        shortcode = extract_shortcode_from_url(post_url)
        if not shortcode:
            return json.dumps({"status": "error", "message": "Could not extract post ID from URL"})

        # Instaloader handles delays with `sleep=True` during setup
        post = None # Initialize post variable
        max_retries = 2
        for attempt in range(max_retries + 1): # +1 for the initial try
            try:
                post = instaloader.Post.from_shortcode(L.context, shortcode)
                break # Success
            except instaloader.exceptions.PrivateProfileNotFollowedException:
                 return json.dumps({"status": "private", "message": "Post is private or requires login"})
            except instaloader.exceptions.LoginRequiredException:
                 return json.dumps({"status": "private", "message": "Login required to access this post"})
            except instaloader.exceptions.RateLimitException:
                 if attempt < max_retries:
                      wait_time = random.uniform(5, 10) * (attempt + 1) # Exponential backoff
                      print(f"Rate limited. Retrying in {wait_time:.1f} seconds...")
                      time.sleep(wait_time)
                 else:
                      return json.dumps({"status": "rate_limited", "message": "Instagram rate limit exceeded. Try again later."})
            except instaloader.exceptions.ConnectionException as e:
                 if attempt < max_retries:
                      wait_time = random.uniform(3, 6) * (attempt + 1)
                      print(f"Connection error: {e}. Retrying in {wait_time:.1f} seconds...")
                      time.sleep(wait_time)
                 else:
                      return json.dumps({"status": "error", "message": f"Connection error after retries: {str(e)[:100]}"})
            except Exception as e: # Catch other potential Instaloader errors
                error_msg = str(e).lower()
                # Check for common error patterns even if specific exception type changes
                if any(keyword in error_msg for keyword in ['404 not found', 'post not found', 'unavailable']):
                     return json.dumps({"status": "not_found", "message": "Post not found or unavailable."})
                if attempt < max_retries:
                     wait_time = random.uniform(4, 7) * (attempt + 1)
                     print(f"Unexpected error fetching post: {e}. Retrying in {wait_time:.1f} seconds...")
                     time.sleep(wait_time)
                else:
                    return json.dumps({"status": "error", "message": f"Failed to fetch post after retries: {str(e)[:100]}"})

        if post is None: # Check if post was successfully fetched after retries
             return json.dumps({"status": "error", "message": "Failed to retrieve post details after multiple attempts."})

        media_items = []
        username = post.owner_username or "unknown_user"

        try:
            if post.typename == "GraphSidecar":
                # Sort nodes just in case they aren't sequential, using index if available
                nodes = sorted(list(post.get_sidecar_nodes()), key=lambda node: getattr(node, 'position', 0))
                for i, node in enumerate(nodes):
                    media_url = node.video_url if node.is_video else node.display_url
                    media_type = "video" if node.is_video else "image"
                    if media_url: # Ensure URL is not None
                        media_items.append({
                            "url": media_url, # Directly use the URL provided by Instaloader
                            "type": media_type,
                            "index": i + 1,
                        })
                    else:
                        print(f"Warning: Missing URL for sidecar node {i+1} in post {shortcode}")
            else:
                media_url = post.video_url if post.is_video else post.url
                media_type = "video" if post.is_video else "image"
                if media_url: # Ensure URL is not None
                    media_items.append({
                        "url": media_url, # Directly use the URL
                        "type": media_type,
                        "index": 1,
                    })
                else:
                    print(f"Warning: Missing URL for single media post {shortcode}")


        except Exception as e:
            return json.dumps({"status": "error", "message": f"Failed to extract media: {str(e)[:100]}"})

        if not media_items:
            # This might happen if URLs were missing or post structure was unexpected
            return json.dumps({"status": "error", "message": "No valid media URLs found in this post"})

        return json.dumps({
            "status": "success",
            "username": username,
            "media": media_items,
            "media_count": len(media_items),
            "caption": post.caption or "",
            "post_date": post.date_utc.isoformat() if hasattr(post, 'date_utc') else None,
            "likes": getattr(post, 'likes', 0),
            "comments": getattr(post, 'comments', 0),
            "shortcode": shortcode,
            "extracted_at": datetime.utcnow().isoformat(),
            "post_url": post_url # Keep original post URL for reference
        }, ensure_ascii=False)

    except Exception as e:
        # Generic fallback error
        return json.dumps({
            "status": "error",
            "message": f"Unexpected Python error: {str(e)[:100]}"
        }, ensure_ascii=False)

def extract_shortcode_from_url(url):
    """Extract shortcode from various Instagram URL patterns"""
    patterns = [
        r'/p/([A-Za-z0-9_-]+)',
        r'/reel/([A-Za-z0-9_-]+)',
        r'/reels/([A-Za-z0-9_-]+)',
        r'/tv/([A-Za-z0-9_-]+)',
        # Add story pattern if needed, though stories are harder to handle
        # r'/stories/([^/]+)/([0-9]+)'
    ]
    for pattern in patterns:
        match = re.search(pattern, url)
        if match:
            return match.group(1) # Return the first captured group (the shortcode)
    return None

# Keep typo'd function for compatibility if old Android code calls it
def get_meida_urls(post_url):
    return get_media_urls(post_url)

def main():
    if len(sys.argv) > 1:
        url = sys.argv[1]
        result = get_media_urls(url)
        print(result)
    else:
        # Provide more helpful usage message
        print(json.dumps({"status": "error", "message": "Usage: python insta_downloader.py <instagram_url>"}))

if __name__ == "__main__":
    main()