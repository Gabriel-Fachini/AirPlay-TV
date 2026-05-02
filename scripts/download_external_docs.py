#!/usr/bin/env python3
"""
Download a curated offline snapshot of AirPlay-related documentation.

The goal is not to mirror the whole internet. It pulls the sources that are
most useful for this repository:
- Unofficial AirPlay protocol documentation
- AirPlay 2 reverse-engineering notes
- Apple archived AirPlay / Bonjour guides
- Android API reference pages used by the MVP
- Core IETF RFCs behind the networking/media stack
"""

from __future__ import annotations

import json
import re
import sys
import time
from collections import deque
from dataclasses import dataclass
from html import unescape
from pathlib import Path
from typing import Iterable
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_ROOT = ROOT / "vendor-docs"
USER_AGENT = "airplay-tv-mvp-doc-mirror/1.0 (+local research snapshot)"


@dataclass(frozen=True)
class CrawlTarget:
    name: str
    start_url: str
    prefixes: tuple[str, ...]
    output_dir: str
    max_pages: int = 250


@dataclass(frozen=True)
class FileTarget:
    url: str
    output_path: str


CRAWL_TARGETS: tuple[CrawlTarget, ...] = (
    CrawlTarget(
        name="openairplay-spec",
        start_url="https://openairplay.github.io/airplay-spec/",
        prefixes=("https://openairplay.github.io/airplay-spec/",),
        output_dir="sites/openairplay-spec",
        max_pages=300,
    ),
    CrawlTarget(
        name="airplay2-internals",
        start_url="https://emanuelecozzi.net/docs/airplay2/",
        prefixes=("https://emanuelecozzi.net/docs/airplay2/",),
        output_dir="sites/airplay2-internals",
        max_pages=120,
    ),
    CrawlTarget(
        name="apple-airplay-guide",
        start_url=(
            "https://developer.apple.com/library/archive/documentation/"
            "AudioVideo/Conceptual/AirPlayGuide/Introduction/Introduction.html"
        ),
        prefixes=(
            "https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/AirPlayGuide/",
            "https://developer.apple.com/library/archive/Resources/",
        ),
        output_dir="sites/apple-airplay-guide",
        max_pages=80,
    ),
    CrawlTarget(
        name="apple-bonjour-overview",
        start_url=(
            "https://developer.apple.com/library/archive/documentation/"
            "Cocoa/Conceptual/NetServices/Introduction.html"
        ),
        prefixes=(
            "https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/NetServices/",
            "https://developer.apple.com/library/archive/documentation/Networking/Conceptual/dns_discovery_api/",
            "https://developer.apple.com/library/archive/documentation/Networking/Conceptual/NSNetServiceProgGuide/",
            "https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/NetworkingOverview/",
            "https://developer.apple.com/library/archive/documentation/NetworkingInternet/Conceptual/NetworkingConcepts/",
            "https://developer.apple.com/library/archive/Resources/",
        ),
        output_dir="sites/apple-bonjour-overview",
        max_pages=120,
    ),
)


FILE_TARGETS: tuple[FileTarget, ...] = (
    FileTarget(
        url="https://developer.android.com/reference/android/net/nsd/NsdManager",
        output_path="android/NsdManager.html",
    ),
    FileTarget(
        url="https://developer.android.com/reference/android/media/MediaCodec",
        output_path="android/MediaCodec.html",
    ),
    FileTarget(
        url="https://developer.android.com/reference/android/media/AudioTrack",
        output_path="android/AudioTrack.html",
    ),
    FileTarget(
        url="https://www.rfc-editor.org/rfc/rfc2326.txt",
        output_path="rfc/rfc2326-rtsp-1.0.txt",
    ),
    FileTarget(
        url="https://www.rfc-editor.org/rfc/rfc3550.txt",
        output_path="rfc/rfc3550-rtp.txt",
    ),
    FileTarget(
        url="https://www.rfc-editor.org/rfc/rfc3640.txt",
        output_path="rfc/rfc3640-mpeg4-elementary-streams-over-rtp.txt",
    ),
    FileTarget(
        url="https://www.rfc-editor.org/rfc/rfc4571.txt",
        output_path="rfc/rfc4571-rtp-over-tcp.txt",
    ),
    FileTarget(
        url="https://www.rfc-editor.org/rfc/rfc6184.txt",
        output_path="rfc/rfc6184-h264-over-rtp.txt",
    ),
    FileTarget(
        url="https://www.rfc-editor.org/rfc/rfc6762.txt",
        output_path="rfc/rfc6762-mdns.txt",
    ),
    FileTarget(
        url="https://www.rfc-editor.org/rfc/rfc6763.txt",
        output_path="rfc/rfc6763-dnssd.txt",
    ),
    FileTarget(
        url="https://nto.github.io/AirPlay.html",
        output_path="historical/nto-unofficial-airplay-spec.html",
    ),
)


def main() -> int:
    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
    manifest: dict[str, object] = {
        "generated_at_epoch": int(time.time()),
        "generated_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "sites": [],
        "files": [],
    }

    for target in CRAWL_TARGETS:
        print(f"[crawl] {target.name}: {target.start_url}")
        result = crawl_site(target)
        manifest["sites"].append(result)

    for target in FILE_TARGETS:
        print(f"[file]  {target.url}")
        path, content_type, size = fetch_to_path(target.url, OUTPUT_ROOT / target.output_path)
        manifest["files"].append(
            {
                "url": target.url,
                "path": str(path.relative_to(ROOT)),
                "content_type": content_type,
                "size_bytes": size,
            }
        )

    manifest_path = OUTPUT_ROOT / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
    print(f"[done] Wrote manifest: {manifest_path}")
    return 0


def crawl_site(target: CrawlTarget) -> dict[str, object]:
    out_dir = OUTPUT_ROOT / target.output_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    queue: deque[str] = deque([target.start_url])
    visited: set[str] = set()
    saved: list[str] = []

    while queue and len(visited) < target.max_pages:
        url = normalize_url(queue.popleft())
        if url in visited:
            continue
        if not any(url.startswith(prefix) for prefix in target.prefixes):
            continue

        visited.add(url)
        try:
            local_path, content_type, _ = fetch_to_path(url, out_dir / local_relpath_from_url(url))
        except Exception as exc:  # pragma: no cover - network failures are non-deterministic
            print(f"  ! failed {url}: {exc}", file=sys.stderr)
            continue

        saved.append(str(local_path.relative_to(ROOT)))

        if "html" not in content_type and "xml" not in content_type:
            continue

        try:
            html = local_path.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue

        for link in extract_links(html):
            absolute = normalize_url(urljoin(url, link))
            if should_follow(absolute, target.prefixes):
                queue.append(absolute)

    return {
        "name": target.name,
        "start_url": target.start_url,
        "prefixes": list(target.prefixes),
        "pages_saved": len(saved),
        "output_dir": str(out_dir.relative_to(ROOT)),
    }


def fetch_to_path(url: str, path: Path) -> tuple[Path, str, int]:
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=30) as response:
        data = response.read()
        content_type = response.headers.get_content_type()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    time.sleep(0.15)
    return path, content_type, len(data)


def local_relpath_from_url(url: str) -> Path:
    parsed = urlparse(url)
    path = parsed.path or "/"
    if path.endswith("/"):
        path = f"{path}index.html"
    name = Path(path.lstrip("/"))
    if not name.suffix:
        name = name / "index.html"
    if parsed.query:
        suffix = name.suffix or ".html"
        stem = name.stem if name.suffix else name.name
        sanitized = sanitize_filename(parsed.query)
        name = name.with_name(f"{stem}__{sanitized}{suffix}")
    return name


def extract_links(html: str) -> set[str]:
    links = set()
    for match in re.findall(r"""(?:href|src)=["']([^"']+)["']""", html, flags=re.IGNORECASE):
        link = unescape(match.strip())
        if not link or link.startswith("#"):
            continue
        if link.startswith(("mailto:", "javascript:", "data:")):
            continue
        links.add(link)
    return links


def should_follow(url: str, prefixes: Iterable[str]) -> bool:
    if not url.startswith(("http://", "https://")):
        return False
    if any(url.startswith(prefix) for prefix in prefixes):
        return True
    return False


def normalize_url(url: str) -> str:
    parsed = urlparse(url)
    clean = parsed._replace(fragment="")
    url = clean.geturl()
    if url.endswith("/index.html"):
        url = url[: -len("index.html")]
    return url


def sanitize_filename(value: str) -> str:
    value = re.sub(r"[^A-Za-z0-9._-]+", "_", value)
    return value[:80] or "query"


if __name__ == "__main__":
    raise SystemExit(main())
