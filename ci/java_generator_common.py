# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools
"""Shared helpers for Java documentation and sample generators."""

from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIMARY_RESOURCES = ROOT / "resources"
PACKAGED_RESOURCES = ROOT / "src" / "main" / "resources"
LAUNCHER = ROOT / "scripts" / "fixdecoder"
TARGET_DIR = ROOT / "target"


def ensure_java_app() -> None:
    """Build the runnable Java artifact when it is missing or stale."""
    jar = find_app_jar() or TARGET_DIR / "fixdecoder-java-0.3.0.jar"
    if jar_is_stale(jar):
        mvn_cmd = os.environ.get("FIXDECODER_MVN") or os.environ.get("MVN") or str(ROOT / "mvnw")
        subprocess.run(
            [mvn_cmd, "-q", "-DskipTests", "package"],
            cwd=ROOT,
            check=True,
        )
    subprocess.run(
        [str(LAUNCHER), "--version"],
        cwd=ROOT,
        stdout=subprocess.DEVNULL,
        check=True,
    )


def run_java_app(args: list[str], stdin: str | None = None) -> subprocess.CompletedProcess[str]:
    """Run the checked-in Java wrapper with generation-safe environment defaults."""
    env = os.environ.copy()
    env.pop("FIXDECODER_DEFAULT_ARGS", None)
    env["PAGER"] = "cat"
    return subprocess.run(
        [str(LAUNCHER), *args],
        cwd=ROOT,
        input=stdin,
        text=True,
        capture_output=True,
        check=False,
        env=env,
    )


def find_app_jar() -> Path | None:
    """Find the newest shaded fixdecoder jar under target/."""
    if not TARGET_DIR.exists():
        return None
    jars = sorted(
        path
        for path in TARGET_DIR.glob("fixdecoder-java-*.jar")
        if not path.name.startswith("original-")
    )
    return jars[-1] if jars else None


def jar_is_stale(jar: Path) -> bool:
    """Return true when Java sources/resources or the POM are newer than the jar."""
    if not jar.exists():
        return True
    jar_mtime = jar.stat().st_mtime
    candidates = [ROOT / "pom.xml", *(ROOT / "src" / "main").rglob("*")]
    return any(path.is_file() and path.stat().st_mtime > jar_mtime for path in candidates)


def load_generated_files(output_dir: Path, skip_names: set[str] | None = None) -> dict[Path, str]:
    """Read generated files under an output directory, excluding permanent docs."""
    skipped = skip_names if skip_names is not None else {"README.md"}
    current: dict[Path, str] = {}
    if not output_dir.exists():
        return current
    for path in output_dir.rglob("*"):
        if not path.is_file() or path.name in skipped:
            continue
        current[path] = path.read_text()
    return current


def relocate_files(expected_files: dict[Path, str], source_root: Path, target_root: Path) -> dict[Path, str]:
    """Move generated-file keys from one resource tree root to another."""
    return {
        target_root / path.relative_to(source_root): content
        for path, content in expected_files.items()
    }


def write_generated_files(
    expected_files: dict[Path, str],
    output_dir: Path,
    ensure_dirs: list[Path] | None = None,
) -> None:
    """Write generated files, deleting stale generated outputs only in that tree."""
    output_dir.mkdir(parents=True, exist_ok=True)
    for directory in ensure_dirs or []:
        directory.mkdir(parents=True, exist_ok=True)

    current_files = load_generated_files(output_dir)
    for path in current_files:
        if path not in expected_files:
            path.unlink()

    for path, content in expected_files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)


def mirror_resource_file(source: Path) -> None:
    """Copy one file from resources/ to src/main/resources/."""
    target = PACKAGED_RESOURCES / source.relative_to(PRIMARY_RESOURCES)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
