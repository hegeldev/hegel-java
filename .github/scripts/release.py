"""Release automation for hegel-java.

Run by the `release` job in ci.yml on a push to main when a RELEASE.md is present. It bumps the
pom's current <version> by the RELEASE_TYPE, writes it back, prepends the changelog, publishes to
Maven Central via the `release` profile, and then records the release in git (commit + tag +
GitHub release).

The publish happens *before* the tag is pushed: if `mvn deploy` fails, nothing is committed or
tagged, so a retry starts clean. `push-or-pr` pushes the release commit to main afterwards,
falling back to a PR if main has diverged.
"""

import argparse
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
POM = ROOT / "pom.xml"


def git(*args: str) -> None:
    subprocess.run(["git", *args], check=True, cwd=ROOT)


def is_source_file(path: str) -> bool:
    # A PR that changes published source (src/main/**) or the pom must carry a RELEASE.md;
    # test-only and tooling changes don't. Mirrors the per-library source definition the other
    # Hegel libraries use (which likewise exclude their test trees).
    return path.startswith("src/main/") or path == "pom.xml"


def parse_release_file(path: Path) -> tuple[str, str]:
    text = path.read_text()
    first_line, _, rest = text.partition("\n")

    match = re.match(r"^RELEASE_TYPE: (major|minor|patch)$", first_line)
    if not match:
        raise ValueError(f"Expected RELEASE_TYPE: major|minor|patch, got {first_line!r}")

    content = rest.strip()
    if not content:
        raise ValueError("Changelog cannot be empty.")

    return match.group(1), content


def bump_version(current: str, release_type: str) -> str:
    major, minor, patch = (int(p) for p in current.split("."))

    if release_type == "major":
        major, minor, patch = major + 1, 0, 0
    elif release_type == "minor":
        minor, patch = minor + 1, 0
    else:
        assert release_type == "patch"
        patch += 1

    return f"{major}.{minor}.{patch}"


def pom_version() -> str:
    """The last released version, read from the pom's project <version> (the first <version>
    element; modelVersion uses a different tag). Seeded at ``0.0.0`` before the first release, so
    the bootstrap ``RELEASE_TYPE: minor`` lands at ``0.1.0``."""
    match = re.search(r"<version>([^<]+)</version>", POM.read_text())
    if match is None:
        raise ValueError("could not find <version> in pom.xml")
    return match.group(1)


def set_pom_version(new_version: str) -> None:
    text = POM.read_text()
    POM.write_text(re.sub(r"<version>[^<]+</version>", f"<version>{new_version}</version>", text, count=1))


def add_changelog(path: Path, *, version: str, content: str) -> None:
    date = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    entry = f"## {version} - {date}\n\n{content}\n"

    existing = path.read_text()
    # Insert above the first existing release section, preserving the header and any preamble.
    idx = existing.find("\n## ")
    if idx == -1:
        sep = "" if existing.endswith("\n") else "\n"
        path.write_text(f"{existing}{sep}\n{entry}")
    else:
        path.write_text(f"{existing[: idx + 1]}\n{entry}{existing[idx + 1 :]}")


def check(base_ref: str) -> None:
    """PR gate (run by check-release.yml): if the PR changes source, require a well-formed
    RELEASE.md. A no-op for PRs that touch no source."""
    output = subprocess.check_output(
        ["git", "diff", "--name-only", f"origin/{base_ref}...HEAD"],
        text=True,
        cwd=ROOT,
    )
    changed_files = [line for line in output.splitlines() if line.strip()]

    if not any(is_source_file(f) for f in changed_files):
        return

    release_file = ROOT / "RELEASE.md"

    # RELEASE.md should never already be on the base branch: the release job removes it after
    # cutting a release, so finding one means a release is in progress or has failed.
    process = subprocess.run(
        ["git", "cat-file", "-e", f"origin/{base_ref}:RELEASE.md"],
        capture_output=True,
        cwd=ROOT,
    )
    if process.returncode == 0:
        raise ValueError(
            f"RELEASE.md already exists on {base_ref}. It's possible the CI job "
            "responsible for cutting a new release is in progress, or has failed."
        )

    if not release_file.exists():
        lines = [
            "Every pull request to hegel-java that changes source requires a RELEASE.md file.",
            "You can find an example and instructions in RELEASE-sample.md.",
            "Label the PR 'skip release' to bypass this (e.g. docs- or CI-only changes).",
        ]
        width = max(len(l) for l in lines) + 6
        border = " ".join("*" * ((width + 1) // 2))
        empty = "*" + " " * (width - 2) + "*"
        inner = "\n".join("*" + l.center(width - 2) + "*" for l in lines)
        pad = "\t"
        box = f"\n{pad}{border}\n{pad}{empty}\n{pad}{empty}\n"
        box += "\n".join(f"{pad}" + l for l in inner.split("\n"))
        box += f"\n{pad}{empty}\n{pad}{empty}\n{pad}{border}\n"
        raise ValueError(box)

    # Validate the first line (RELEASE_TYPE) and that the changelog body is non-empty.
    parse_release_file(release_file)


def release() -> None:
    release_file = ROOT / "RELEASE.md"
    assert release_file.exists()

    release_type, content = parse_release_file(release_file)
    new_version = bump_version(pom_version(), release_type)

    set_pom_version(new_version)
    add_changelog(ROOT / "CHANGELOG.md", version=new_version, content=content)

    # Publish to Maven Central. The release profile builds + signs the jar/sources/javadoc,
    # fails loudly if the bundled natives are missing, and uploads + publishes the deployment
    # (autoPublish + waitUntil=published are configured on the plugin). Done before any git tag
    # so a failure leaves nothing to unwind.
    mvn_args = ["mvn", "-B", "-P", "release", "deploy"]
    # Pin the signing key by fingerprint (GPG_KEYNAME) so the build signs with the Hegel release
    # key specifically and fails if that key isn't the one in the keyring, rather than silently
    # falling back to whatever gpg's default key is.
    keyname = os.environ.get("GPG_KEYNAME")
    if keyname:
        mvn_args.append(f"-Dgpg.keyname={keyname}")
    subprocess.run(mvn_args, check=True, cwd=ROOT)

    app_slug = os.environ["HEGEL_RELEASE_APP_SLUG"]
    bot_user_id = subprocess.run(
        ["gh", "api", f"/users/{app_slug}[bot]", "--jq", ".id"],
        check=True,
        text=True,
        capture_output=True,
    ).stdout.strip()
    git("config", "user.name", f"{app_slug}[bot]")
    git("config", "user.email", f"{bot_user_id}+{app_slug}[bot]@users.noreply.github.com")

    git("add", "pom.xml", "CHANGELOG.md")
    git("rm", "RELEASE.md")
    git("commit", "-m", f"Bump to version {new_version} and update changelog\n\n[skip ci]")
    git("tag", f"v{new_version}")
    git("push", "origin", f"v{new_version}")

    subprocess.run(
        ["gh", "release", "create", f"v{new_version}", "--title", f"v{new_version}", "--notes", content],
        check=True,
        cwd=ROOT,
    )


def push_or_pr() -> None:
    """Push the release commit to main, or open a PR if main has diverged. The Central publish
    and tag already succeeded by this point, so this only lands the bookkeeping commit."""
    if subprocess.run(["git", "push", "origin", "main"], cwd=ROOT).returncode == 0:
        return

    version = pom_version()
    print(f"Push to main failed; opening a PR for release v{version}")

    branch = f"release/v{version}"
    git("checkout", "-b", branch)
    git("push", "origin", branch)

    # Ensure the "skip release" label exists so check-release doesn't re-run on this PR.
    subprocess.run(
        ["gh", "label", "create", "skip release", "--force", "--description", "Skip the release check on this PR"],
        cwd=ROOT,
    )
    subprocess.run(
        [
            "gh", "pr", "create",
            "--base", "main",
            "--head", branch,
            "--title", f"Release v{version}",
            "--body",
            f"The push to main after publishing v{version} failed because main had diverged. "
            f"The Maven Central publish and the v{version} tag already succeeded.\n\n"
            f"This PR merges the release commit (version bump, changelog, RELEASE.md removal) into main.",
            "--label", "skip release",
        ],
        check=True,
        cwd=ROOT,
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Release automation for hegel-java.")
    subparsers = parser.add_subparsers(dest="command", required=True)
    check_parser = subparsers.add_parser("check")
    check_parser.add_argument("base_ref", help="Git ref (branch) to diff against.")
    subparsers.add_parser("release")
    subparsers.add_parser("push-or-pr")

    args = parser.parse_args()
    if args.command == "check":
        check(args.base_ref)
    elif args.command == "release":
        release()
    elif args.command == "push-or-pr":
        push_or_pr()
