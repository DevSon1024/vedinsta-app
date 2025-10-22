import instaloader
import json
import sys
import re
from datetime import datetime

def setup_instaloader():
    """Setup and configure Instaloader instance"""
    try:
        L = instaloader.Instaloader(
            download_video_thumbnails=False,
            download_geotags=False,
            download_comments=False,
            save_metadata=False,
            compress_json=False,
            request_timeout=20,
            max_connection_attempts=3,
            sleep=False,
            quiet=True
        )
        return L
    except Exception as e:
        print(f"ERROR: Failed to setup Instaloader: {e}")
        return None

def extract_shortcode_from_url(url):
    """Extract Instagram post shortcode from URL"""
    # Support various Instagram URL formats
    patterns = [
        r'/p/([A-Za-z0-9_-]+)',      # Post URLs
        r'/reel/([A-Za-z0-9_-]+)',   # Reel URLs
        r'/tv/([A-Za-z0-9_-]+)',     # IGTV URLs
    ]

    for pattern in patterns:
        match = re.search(pattern, url)
        if match:
            return match.group(1)
    return None

def get_highest_quality_url(url):
    """Convert Instagram media URL to highest quality version"""
    if not url:
        return url

    try:
        # Remove quality reducing parameters
        if 'instagram.com' in url or 'cdninstagram.com' in url:
            # Remove quality modifiers for images
            url = url.replace('_n.jpg', '.jpg')
            url = url.replace('_a.jpg', '.jpg')
            url = url.replace('_t.jpg', '.jpg')

            # Remove quality modifiers for videos
            url = url.replace('_n.mp4', '.mp4')
            url = url.replace('_a.mp4', '.mp4')
            url = url.replace('_t.mp4', '.mp4')

            # Add parameters for original quality
            separator = '&' if '?' in url else '?'
            url += f'{separator}_nc_ohc=original&_nc_ht=original'

        return url
    except Exception:
        return url

def is_restricted_error(error_message):
    """Check if error indicates private/restricted content"""
    restricted_keywords = [
        '429', 'rate limit', '403', 'forbidden',
        'private', 'login', 'challenge', 'checkpoint',
        'not found', '404'
    ]
    error_lower = str(error_message).lower()
    return any(keyword in error_lower for keyword in restricted_keywords)

def get_media_urls(post_url):
    """
    Main function called from Kotlin/Android app.
    Returns JSON string with media URLs and metadata.
    """
    try:
        # Validate input
        if not post_url or not isinstance(post_url, str):
            return json.dumps({
                "status": "error",
                "message": "Invalid URL provided"
            })

        # Clean and validate URL
        post_url = post_url.strip()
        if 'instagram.com' not in post_url:
            return json.dumps({
                "status": "error",
                "message": "Not a valid Instagram URL"
            })

        # Setup Instaloader
        L = setup_instaloader()
        if not L:
            return json.dumps({
                "status": "error",
                "message": "Failed to initialize Instagram loader"
            })

        # Extract shortcode from URL
        shortcode = extract_shortcode_from_url(post_url)
        if not shortcode:
            return json.dumps({
                "status": "error",
                "message": "Could not extract post ID from URL"
            })

        # Fetch post with retries
        max_retries = 2
        for attempt in range(max_retries):
            try:
                post = instaloader.Post.from_shortcode(L.context, shortcode)
                break
            except Exception as e:
                if attempt == max_retries - 1:  # Last attempt
                    error_msg = str(e)
                    if is_restricted_error(error_msg):
                        return json.dumps({
                            "status": "private",
                            "message": "Post is private, deleted, or requires login"
                        })
                    return json.dumps({
                        "status": "error",
                        "message": f"Failed to fetch post: {error_msg[:100]}"
                    })
                continue

        # Extract media information
        media_items = []
        username = post.owner_username or "unknown_user"

        try:
            # Handle different post types
            if post.typename == "GraphSidecar":
                # Carousel post (multiple images/videos)
                sidecar_nodes = list(post.get_sidecar_nodes())
                for node in sidecar_nodes:
                    if node.is_video:
                        media_url = get_highest_quality_url(node.video_url)
                        media_type = "video"
                    else:
                        media_url = get_highest_quality_url(node.display_url)
                        media_type = "image"

                    media_items.append({
                        "url": media_url,
                        "type": media_type
                    })

            else:
                # Single post (image or video)
                if post.is_video:
                    media_url = get_highest_quality_url(post.video_url)
                    media_type = "video"
                else:
                    media_url = get_highest_quality_url(post.url)
                    media_type = "image"

                media_items.append({
                    "url": media_url,
                    "type": media_type
                })

        except Exception as e:
            return json.dumps({
                "status": "error",
                "message": f"Failed to extract media: {str(e)[:100]}"
            })

        # Validate we have media
        if not media_items:
            return json.dumps({
                "status": "error",
                "message": "No media found in this post"
            })

        # Return successful result
        return json.dumps({
            "status": "success",
            "username": username,
            "media": media_items,
            "caption": post.caption or "", # <-- UPDATED: Removed truncation
            "post_date": post.date_utc.isoformat() if hasattr(post, 'date_utc') else None,
            "likes": getattr(post, 'likes', 0),
            "comments": getattr(post, 'comments', 0)
        }, ensure_ascii=False)

    except Exception as e:
        # Catch-all error handler
        return json.dumps({
            "status": "error",
            "message": f"Unexpected error: {str(e)[:100]}"
        }, ensure_ascii=False)

def get_meida_urls(post_url):
    """
    Backup function name with typo for compatibility.
    This handles the error you're seeing.
    """
    return get_media_urls(post_url)

# For direct Python execution (testing)
def main():
    """For testing the script directly"""
    if len(sys.argv) > 1:
        url = sys.argv[1]
        result = get_media_urls(url)
        print(result)
    else:
        # Test with a sample URL (replace with actual Instagram URL for testing)
        test_url = "https://www.instagram.com/p/SAMPLE_POST_ID/"
        print("Please provide an Instagram URL as argument")
        print(f"Usage: python insta_downloader.py {test_url}")

if __name__ == "__main__":
    main()