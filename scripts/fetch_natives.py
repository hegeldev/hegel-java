#!/usr/bin/env python3
"""Fetch the libhegel native libraries for a release and stage them for bundling.

This runs at build time (wired into Maven's ``generate-resources`` phase). It discovers
*every* shared object the matching hegel-rust release actually publishes — rather than a
hardcoded platform list — so a release that adds (or omits) a platform is handled with no
code change. The fetched libraries are copied into the build output as classpath resources
under ``native/<os>-<arch>/libhegel.<ext>``; at runtime ``LibraryLoader`` unpacks the one
matching the host from the jar. No network access is needed by end users.

Downloads are cached per version under the user cache dir, so repeated builds (and builds
after ``mvn clean``) reuse them without hitting the network. If the network is unavailable
and nothing is cached, this prints a warning and exits 0: the build still succeeds, and the
runtime falls back to ``$HEGEL_LIBHEGEL_PATH`` or a sibling ``hegel-rust`` checkout. A real
release build runs in CI with network, so the published jar is always complete.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import urllib.error
import urllib.request
from pathlib import Path

# Asset names look like ``libhegel-linux-amd64.so`` / ``libhegel-darwin-arm64.dylib``.
ASSET_RE = re.compile(r"^libhegel-([A-Za-z0-9]+)-([A-Za-z0-9]+)\.(so|dylib)$")
DEFAULT_REPO = "hegeldev/hegel-rust"


def log(msg: str) -> None:
    print(f"[fetch-natives] {msg}", file=sys.stderr)


def cache_root() -> Path:
    xdg = os.environ.get("XDG_CACHE_HOME")
    base = Path(xdg) if xdg else Path.home() / ".cache"
    return base / "hegel-java" / "natives"


def http_get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "hegel-java-build"})
    token = os.environ.get("GITHUB_TOKEN")
    # A token only matters for the api.github.com enumeration request (rate limits); the
    # release-asset CDN URLs are public and need none, but sending it is harmless.
    if token and url.startswith("https://api.github.com/"):
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=60) as resp:  # follows 302 redirects
        return resp.read()


def discover_assets(repo: str, version: str) -> dict[str, str]:
    """Return ``{asset_name: download_url}`` for the release tagged ``v<version>``."""
    api = f"https://api.github.com/repos/{repo}/releases/tags/v{version}"
    release = json.loads(http_get(api))
    return {a["name"]: a["browser_download_url"] for a in release.get("assets", [])}


def expected_sha256(assets: dict[str, str], lib_name: str) -> str | None:
    url = assets.get(lib_name + ".sha256")
    if url is None:
        return None
    # GNU sha256sum style: "<digest>  <filename>".
    return http_get(url).decode("utf-8").split()[0].lower()


def populate_cache(repo: str, version: str, cache: Path) -> None:
    assets = discover_assets(repo, version)
    libs = {name: url for name, url in assets.items() if ASSET_RE.match(name)}
    if not libs:
        log(f"release v{version} of {repo} publishes no libhegel shared objects")
        return
    cache.mkdir(parents=True, exist_ok=True)
    for name, url in sorted(libs.items()):
        data = http_get(url)
        want = expected_sha256(assets, name)
        if want is not None:
            got = hashlib.sha256(data).hexdigest()
            if got != want:
                raise SystemExit(f"checksum mismatch for {name}: expected {want}, got {got}")
        else:
            log(f"no published checksum for {name}; bundling without verification")
        (cache / name).write_bytes(data)
        log(f"fetched {name} ({len(data)} bytes)")


def cached_libs(cache: Path) -> list[Path]:
    return [p for p in cache.glob("libhegel-*") if ASSET_RE.match(p.name)]


def stage(libs: list[Path], out: Path) -> None:
    for lib in libs:
        m = ASSET_RE.match(lib.name)
        goos, goarch, ext = m.group(1), m.group(2), m.group(3)
        dest_dir = out / "native" / f"{goos}-{goarch}"
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / f"libhegel.{ext}"
        shutil.copyfile(lib, dest)
        log(f"staged {lib.name} -> {dest.relative_to(out)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="libhegel/hegel-rust release version")
    parser.add_argument("--out", required=True, type=Path, help="build output resource dir")
    parser.add_argument("--repo", default=DEFAULT_REPO, help="GitHub owner/repo of the engine")
    args = parser.parse_args()

    # Cache is keyed by version, so a version bump re-discovers (and picks up new platforms).
    cache = cache_root() / f"v{args.version}"
    if not cached_libs(cache):
        try:
            populate_cache(args.repo, args.version, cache)
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            if cached_libs(cache):
                log(f"network error ({e}); using cached libraries")
            else:
                log(
                    f"could not fetch libhegel natives ({e}) and none are cached; "
                    "the jar will not bundle natives. The runtime can still use "
                    "$HEGEL_LIBHEGEL_PATH or a sibling hegel-rust checkout."
                )
                return 0

    libs = cached_libs(cache)
    if not libs:
        log("no native libraries to stage")
        return 0
    stage(libs, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
