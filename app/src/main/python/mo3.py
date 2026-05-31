import requests, json, sys, http.cookiejar
from pathlib import Path


def load_cookies(cookie_file: str) -> dict:
    jar = http.cookiejar.MozillaCookieJar()
    jar.load(cookie_file, ignore_discard=True, ignore_expires=True)
    return {c.name: c.value for c in jar if "instagram.com" in c.domain}


def shortcode_to_id(sc: str) -> str:
    alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    n = 0
    for c in sc:
        n = n * 64 + alpha.index(c)
    return str(n)


def extract_instagram(shortcode: str, cookie_file: str = "instagram_cookies.txt") -> list:
    if not Path(cookie_file).exists():
        return [{"error": f"Cookie file not found: {cookie_file}"}]

    cookies = load_cookies(cookie_file)
    if "sessionid" not in cookies:
        return [{"error": "sessionid missing — re-export cookies while logged into Instagram"}]

    session = requests.Session()
    session.cookies.update(cookies)
    session.headers.update({
        "User-Agent": "Instagram 319.0.0.28.119 Android",
        "X-IG-App-ID": "567067343352427",
        "X-CSRFToken": cookies.get("csrftoken", ""),
        "Referer": "https://www.instagram.com/",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept": "*/*",
    })

    media_id = shortcode_to_id(shortcode)
    r = session.get(
        f"https://i.instagram.com/api/v1/media/{media_id}/info/",
        timeout=20
    )

    if r.status_code != 200:
        return [{"error": f"API returned {r.status_code}"}]

    return _parse(r.json())


def _parse(data: dict) -> list:
    results = []
    for item in data.get("items", []):
        if item.get("media_type") == 8:  # carousel
            for i, node in enumerate(item.get("carousel_media", []), 1):
                entry = _best(node, i)
                if entry:
                    results.append(entry)
        else:
            entry = _best(item, 1)
            if entry:
                results.append(entry)
    return results


def _best(item: dict, index: int) -> dict | None:
    if item.get("media_type") == 2:  # video
        versions = item.get("video_versions", [])
        if not versions:
            return None
        v = versions[0]
        qualities = []
        for ver in versions:
            if ver.get("url"):
                qualities.append({
                    "url": ver["url"],
                    "width": ver.get("width"),
                    "height": ver.get("height")
                })
        return {
            "url": v["url"],
            "type": "video",
            "width": v.get("width"),
            "height": v.get("height"),
            "index": index,
            "qualities": qualities,
        }
    else:  # image
        candidates = item.get("image_versions2", {}).get("candidates", [])
        if not candidates:
            return None
        c = candidates[0]  # always highest res first
        qualities = []
        for cand in candidates:
            if cand.get("url"):
                qualities.append({
                    "url": cand["url"],
                    "width": cand.get("width"),
                    "height": cand.get("height")
                })
        return {
            "url": c["url"],
            "type": "image",
            "width": c.get("width"),
            "height": c.get("height"),
            "index": index,
            "qualities": qualities,
        }


def get_media_urls(url: str, cookie_file: str = "instagram_cookies.txt") -> str:
    sc = url.strip()
    if "instagram.com/" in sc:
        for segment in ["/p/", "/reel/", "/reels/", "/tv/"]:
            if segment in sc:
                sc = sc.split(segment)[1].strip("/").split("/")[0].split("?")[0]
                break

    if not Path(cookie_file).exists():
        return json.dumps({"status": "error", "message": f"Cookie file not found: {cookie_file}"})

    try:
        cookies = load_cookies(cookie_file)
        if "sessionid" not in cookies:
            return json.dumps({"status": "login_required", "message": "sessionid missing — re-export cookies"})

        session = requests.Session()
        session.cookies.update(cookies)
        session.headers.update({
            "User-Agent": "Instagram 319.0.0.28.119 Android",
            "X-IG-App-ID": "567067343352427",
            "X-CSRFToken": cookies.get("csrftoken", ""),
            "Referer": "https://www.instagram.com/",
            "Accept-Language": "en-US,en;q=0.9",
            "Accept": "*/*",
        })

        media_id = shortcode_to_id(sc)
        r = session.get(
            f"https://i.instagram.com/api/v1/media/{media_id}/info/",
            timeout=20
        )

        if r.status_code != 200:
            if r.status_code in [401, 403]:
                return json.dumps({"status": "login_required", "message": f"API returned {r.status_code}. Login required."})
            return json.dumps({"status": "error", "message": f"API returned {r.status_code}"})

        data = r.json()
        items = data.get("items", [])
        if not items:
            return json.dumps({"status": "not_found", "message": "Post not found or deleted."})

        item = items[0]
        username = item.get("user", {}).get("username", "unknown")
        caption = item.get("caption", {}).get("text", "") if item.get("caption") else ""

        media_list = _parse(data)

        return json.dumps({
            "status": "success",
            "username": username,
            "caption": caption,
            "media": media_list,
            "media_count": len(media_list),
            "shortcode": sc
        })
    except Exception as e:
        return json.dumps({"status": "error", "message": f"Extraction failed: {str(e)}"})


if __name__ == "__main__":
    sc = sys.argv[1] if len(sys.argv) > 1 else "DY9CNEHGlem"
    # Strip full URL if passed
    if "instagram.com/p/" in sc:
        sc = sc.split("/p/")[1].strip("/").split("/")[0]

    results = extract_instagram(sc)
    print(json.dumps(results, indent=2))
