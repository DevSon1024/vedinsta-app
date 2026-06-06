import os
import re
import sys
import argparse
from urllib.parse import urljoin, urlparse
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests
from bs4 import BeautifulSoup


HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
}


def get_folder_name_from_url(url: str) -> str:
    path_parts = [p for p in urlparse(url).path.split("/") if p]
    if len(path_parts) < 2:
        return "downloaded_images"
    # second last segment before index.html
    return path_parts[-2]


def fetch_html(session: requests.Session, url: str) -> str:
    r = session.get(url, headers=HEADERS, timeout=30)
    r.raise_for_status()
    return r.text


def extract_image_page_links(session: requests.Session, gallery_url: str) -> list:
    html = fetch_html(session, gallery_url)
    soup = BeautifulSoup(html, "html.parser")

    links = set()

    for a in soup.find_all("a", href=True):
        href = a["href"].strip()
        full_url = urljoin(gallery_url, href)

        if "/pages/image" in full_url and full_url.endswith(".html"):
            links.add(full_url)

    # fallback: parse raw HTML in case links are generated oddly
    if not links:
        raw_matches = re.findall(r'pages/image\d+\.html', html, flags=re.IGNORECASE)
        for match in raw_matches:
            links.add(urljoin(gallery_url, match))

    return sorted(links)


def extract_full_image_url(session: requests.Session, image_page_url: str) -> str | None:
    try:
        html = fetch_html(session, image_page_url)
        soup = BeautifulSoup(html, "html.parser")

        candidates = []

        for img in soup.find_all("img", src=True):
            src = img["src"].strip()
            full_img_url = urljoin(image_page_url, src)

            # prefer actual gallery images, avoid logos/buttons/ads
            if any(x in full_img_url.lower() for x in [".jpg", ".jpeg", ".png", ".webp"]):
                candidates.append(full_img_url)

        # prefer likely large image paths
        for url in candidates:
            lower = url.lower()
            if "/images/" in lower or "/image" in lower:
                return url

        if candidates:
            return candidates[0]

    except Exception as e:
        print(f"Failed to parse image page: {image_page_url} -> {e}")

    return None


def scrape_image_urls(gallery_url: str) -> list:
    with requests.Session() as session:
        page_links = extract_image_page_links(session, gallery_url)

        if not page_links:
            print("No image detail pages found.")
            return []

        image_urls = []
        seen = set()

        # parallel extraction of image URLs from detail pages
        with ThreadPoolExecutor(max_workers=16) as executor:
            futures = {executor.submit(extract_full_image_url, session, link): link for link in page_links}

            for future in as_completed(futures):
                img_url = future.result()
                if img_url and img_url not in seen:
                    seen.add(img_url)
                    image_urls.append(img_url)

        return sorted(image_urls)


def download_one(session: requests.Session, img_url: str, output_dir: str, index: int) -> str:
    try:
        r = session.get(img_url, headers=HEADERS, timeout=60, stream=True)
        r.raise_for_status()

        original_name = os.path.basename(urlparse(img_url).path)
        ext = os.path.splitext(original_name)[1] or ".jpg"
        filename = f"{index:03d}{ext}"
        filepath = os.path.join(output_dir, filename)

        with open(filepath, "wb") as f:
            for chunk in r.iter_content(chunk_size=1024 * 256):
                if chunk:
                    f.write(chunk)

        return f"Downloaded: {filepath}"
    except Exception as e:
        return f"Failed: {img_url} -> {e}"


def download_images(image_urls: list, folder_name: str, max_workers: int = 32):
    os.makedirs(folder_name, exist_ok=True)

    with requests.Session() as session:
        adapter = requests.adapters.HTTPAdapter(
            pool_connections=max_workers,
            pool_maxsize=max_workers,
            max_retries=2
        )
        session.mount("http://", adapter)
        session.mount("https://", adapter)

        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = [
                executor.submit(download_one, session, url, folder_name, i + 1)
                for i, url in enumerate(image_urls)
            ]

            for future in as_completed(futures):
                print(future.result())


def save_urls_txt(image_urls: list, folder_name: str):
    os.makedirs(folder_name, exist_ok=True)
    txt_path = os.path.join(folder_name, "image_urls.txt")
    with open(txt_path, "w", encoding="utf-8") as f:
        for url in image_urls:
            f.write(url + "\n")
    print(f"Saved URLs to: {txt_path}")


def main():
    parser = argparse.ArgumentParser(description="Scrape and optionally download image URLs from an Idlebrain gallery.")
    parser.add_argument("url", help="Gallery URL, e.g. https://www.idlebrain.com/movie/photogallery/rakulpreetsingh40/index.html")
    parser.add_argument("--download", action="store_true", help="Download all scraped images")
    parser.add_argument("--workers", type=int, default=32, help="Concurrent download workers for max speed")
    args = parser.parse_args()

    gallery_url = args.url
    folder_name = get_folder_name_from_url(gallery_url)

    print(f"Gallery URL: {gallery_url}")
    print(f"Output folder: {folder_name}")

    image_urls = scrape_image_urls(gallery_url)

    if not image_urls:
        print("No image URLs found.")
        sys.exit(1)

    print(f"Found {len(image_urls)} image URLs:\n")
    for url in image_urls:
        print(url)

    save_urls_txt(image_urls, folder_name)

    if args.download:
        print(f"\nDownloading images into ./{folder_name}/ ...")
        download_images(image_urls, folder_name, max_workers=args.workers)
        print("Done.")


if __name__ == "__main__":
    main()