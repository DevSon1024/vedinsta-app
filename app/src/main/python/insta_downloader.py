import instaloader
import json
import sys
import re
import time
import random
from datetime import datetime
import urllib.request
import urllib.parse

def setup_instaloader():
    """Setup Instaloader with session management"""
    try:
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
            user_agent='Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 Instagram 308.0.0.34.113 Android (31/12; 450dpi; 1080x2392; samsung; SM-G991B; o1s; exynos2100; en_US; 458229237)'
        )

        # Configure session with Instagram-specific headers
        if hasattr(L, '_session') and L._session:
            headers = {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 Instagram 308.0.0.34.113 Android (31/12; 450dpi; 1080x2392; samsung; SM-G991B; o1s; exynos2100; en_US; 458229237)',
                'Accept': '*/*',
                'Accept-Language': 'en-US,en;q=0.9',
                'Accept-Encoding': 'gzip, deflate, br',
                'Cache-Control': 'no-cache',
                'Pragma': 'no-cache',
                'sec-ch-ua': '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
                'sec-ch-ua-mobile': '?1',
                'sec-ch-ua-platform': '"Android"',
                'sec-fetch-dest': 'empty',
                'sec-fetch-mode': 'cors',
                'sec-fetch-site': 'cross-site',
                'X-Instagram-AJAX': '1010645805',
                'X-Requested-With': 'XMLHttpRequest'
            }
            L._session.headers.update(headers)

        return L
    except Exception as e:
        print(f"ERROR: Failed to setup Instaloader: {e}")
        return None

def create_media_request(url, post_url):
    """Create a proper request for Instagram media with referrer context"""
    try:
        # Parse the original Instagram post URL to get the referrer
        if '/p/' in post_url:
            referrer = post_url
        elif '/reel/' in post_url:
            referrer = post_url
        else:
            referrer = "https://www.instagram.com/"

        # Create request with proper context
        req = urllib.request.Request(url)

        # Essential headers for Instagram media access
        req.add_header('User-Agent', 'Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 Instagram 308.0.0.34.113 Android')
        req.add_header('Referer', referrer)
        req.add_header('Origin', 'https://www.instagram.com')
        req.add_header('Accept', 'video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5')
        req.add_header('Accept-Language', 'en-US,en;q=0.9')
        req.add_header('Accept-Encoding', 'identity')  # Important: no compression for media
        req.add_header('Cache-Control', 'no-cache')
        req.add_header('Connection', 'keep-alive')
        req.add_header('sec-ch-ua', '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"')
        req.add_header('sec-ch-ua-mobile', '?1')
        req.add_header('sec-ch-ua-platform', '"Android"')
        req.add_header('Sec-Fetch-Dest', 'video')
        req.add_header('Sec-Fetch-Mode', 'cors')
        req.add_header('Sec-Fetch-Site', 'cross-site')
        # Critical for Instagram CDN access
        req.add_header('Range', 'bytes=0-')

        return req
    except Exception as e:
        print(f"Error creating media request: {e}")
        return None

def verify_media_access(url, post_url):
    """Verify that the media URL is accessible with proper headers"""
    try:
        req = create_media_request(url, post_url)
        if not req:
            return False

        # Test the connection
        with urllib.request.urlopen(req, timeout=30) as response:
            # Check if we get a successful response
            return response.getcode() == 200 or response.getcode() == 206  # 206 for partial content (Range request)

    except Exception as e:
        print(f"Media access verification failed: {e}")
        return False

def get_enhanced_media_url(original_url, post_url):
    """Get media URL with enhanced context and verification"""
    try:
        # Clean the URL first
        if not original_url:
            return None

        # Remove problematic parameters but keep essential ones
        if '?' in original_url:
            base_url, params = original_url.split('?', 1)
            essential_params = []

            # Keep only critical Instagram CDN parameters
            for param in params.split('&'):
                if any(keep in param.lower() for keep in ['_nc_', 'oh=', 'oe=', 'ccb=', '_nc_rid', 'efg']):
                    essential_params.append(param)

            if essential_params:
                enhanced_url = f"{base_url}?{'&'.join(essential_params)}"
            else:
                enhanced_url = base_url
        else:
            enhanced_url = original_url

        # Verify the URL is accessible
        if verify_media_access(enhanced_url, post_url):
            return enhanced_url
        else:
            # If verification fails, return the original URL and let the download attempt it
            print(f"Media URL verification failed, returning original URL")
            return original_url

    except Exception as e:
        print(f"Error enhancing media URL: {e}")
        return original_url

def get_media_urls(post_url):
    """
    Enhanced main function with proper Instagram context handling
    """
    try:
        # Validate input
        if not post_url or not isinstance(post_url, str):
            return json.dumps({
                "status": "error",
                "message": "Invalid URL provided"
            })

        post_url = post_url.strip()
        if 'instagram.com' not in post_url:
            return json.dumps({
                "status": "error",
                "message": "Not a valid Instagram URL"
            })

        # Setup enhanced Instaloader
        L = setup_instaloader()
        if not L:
            return json.dumps({
                "status": "error",
                "message": "Failed to initialize Instagram loader"
            })

        # Extract shortcode
        shortcode = extract_shortcode_from_url(post_url)
        if not shortcode:
            return json.dumps({
                "status": "error",
                "message": "Could not extract post ID from URL"
            })

        # Add delay to avoid detection
        time.sleep(random.uniform(2, 4))

        # Fetch post with retry logic
        max_retries = 2
        for attempt in range(max_retries):
            try:
                post = instaloader.Post.from_shortcode(L.context, shortcode)
                break
            except Exception as e:
                error_msg = str(e).lower()
                if attempt == max_retries - 1:
                    if any(keyword in error_msg for keyword in ['403', 'forbidden', 'rate limit', '429']):
                        return json.dumps({
                            "status": "rate_limited",
                            "message": "Instagram is temporarily blocking requests. Please try again in a few minutes."
                        })
                    elif any(keyword in error_msg for keyword in ['private', 'login']):
                        return json.dumps({
                            "status": "private",
                            "message": "Post is private or requires login"
                        })
                    else:
                        return json.dumps({
                            "status": "error",
                            "message": f"Failed to fetch post: {str(e)[:100]}"
                        })
                time.sleep(random.uniform(5, 8))
                continue

        # Extract media with enhanced URL processing
        media_items = []
        username = post.owner_username or "unknown_user"

        try:
            if post.typename == "GraphSidecar":
                sidecar_nodes = list(post.get_sidecar_nodes())
                for i, node in enumerate(sidecar_nodes):
                    if node.is_video:
                        original_url = node.video_url
                        media_type = "video"
                    else:
                        original_url = node.display_url
                        media_type = "image"

                    # Enhanced URL with proper context
                    enhanced_url = get_enhanced_media_url(original_url, post_url)

                    media_items.append({
                        "url": enhanced_url,
                        "type": media_type,
                        "index": i + 1,
                        "post_context": post_url  # Include original post URL for context
                    })
            else:
                if post.is_video:
                    original_url = post.video_url
                    media_type = "video"
                else:
                    original_url = post.url
                    media_type = "image"

                # Enhanced URL with proper context
                enhanced_url = get_enhanced_media_url(original_url, post_url)

                media_items.append({
                    "url": enhanced_url,
                    "type": media_type,
                    "index": 1,
                    "post_context": post_url  # Include original post URL for context
                })

        except Exception as e:
            return json.dumps({
                "status": "error",
                "message": f"Failed to extract media: {str(e)[:100]}"
            })

        if not media_items:
            return json.dumps({
                "status": "error",
                "message": "No media found in this post"
            })

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
            "post_url": post_url  # Include original post URL for download context
        }, ensure_ascii=False)

    except Exception as e:
        return json.dumps({
            "status": "error",
            "message": f"Unexpected error: {str(e)[:100]}"
        }, ensure_ascii=False)

def extract_shortcode_from_url(url):
    """Extract shortcode from Instagram URL"""
    patterns = [
        r'/p/([A-Za-z0-9_-]+)',
        r'/reel/([A-Za-z0-9_-]+)',
        r'/reels/([A-Za-z0-9_-]+)',
        r'/tv/([A-Za-z0-9_-]+)',
    ]

    for pattern in patterns:
        match = re.search(pattern, url)
        if match:
            return match.group(1)
    return None

def get_meida_urls(post_url):
    """Backup function with typo for compatibility"""
    return get_media_urls(post_url)

def main():
    if len(sys.argv) > 1:
        url = sys.argv[1]
        result = get_media_urls(url)
        print(result)
    else:
        print("Usage: python insta_downloader.py <instagram_url>")

if __name__ == "__main__":
    main()