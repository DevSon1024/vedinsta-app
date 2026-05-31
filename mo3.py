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
        return {
            "url": v["url"],
            "type": "video",
            "width": v.get("width"),
            "height": v.get("height"),
            "index": index,
        }
    else:  # image
        candidates = item.get("image_versions2", {}).get("candidates", [])
        if not candidates:
            return None
        c = candidates[0]  # always highest res first
        return {
            "url": c["url"],
            "type": "image",
            "width": c.get("width"),
            "height": c.get("height"),
            "index": index,
        }


if __name__ == "__main__":
    sc = sys.argv[1] if len(sys.argv) > 1 else "DY9CNEHGlem"
    # Strip full URL if passed
    if "instagram.com/p/" in sc:
        sc = sc.split("/p/")[1].strip("/").split("/")[0]

    results = extract_instagram(sc)
    print(json.dumps(results, indent=2))