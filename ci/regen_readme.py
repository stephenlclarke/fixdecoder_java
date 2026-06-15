#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools
"""Regenerate README usage text and Java command-line output examples."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

from java_generator_common import ROOT, ensure_java_app, run_java_app

README = ROOT / "README.md"
USAGE = ROOT / "resources" / "messages" / "usage_en.txt"
EXAMPLE_DIR = ROOT / "target" / "readme-examples"
ANSI_RE = re.compile(r"\x1b\[[0-9;?]*[ -/]*[@-~]")
SOH = "\x01"


@dataclass(frozen=True)
class ReadmeExample:
    """A deterministic README command example rendered by the Java app."""

    option: str
    display_command: str
    args: tuple[str, ...]
    stdin: str | None = None
    max_lines: int = 24
    setup: str | None = None
    allow_failure: bool = False


def main() -> int:
    """Regenerate generated README sections from the current Java CLI."""
    if not README.exists():
        print(f"README not found: {README}", file=sys.stderr)
        return 1
    if not USAGE.exists():
        print(f"Usage text not found: {USAGE}", file=sys.stderr)
        return 1

    ensure_java_app()
    original = README.read_text()
    updated = replace_section(original, "usage", render_usage_section())
    updated = replace_section(updated, "examples", render_examples_section())
    README.write_text(updated)
    print(f"Updated {README.relative_to(ROOT)} with generated usage and CLI examples")
    return 0


def fix_message(fields: list[tuple[str, str]]) -> str:
    """Build a valid single-line FIX.4.4 message for README examples."""
    body = "".join(f"{tag}={value}{SOH}" for tag, value in fields)
    prefix = f"8=FIX.4.4{SOH}9={len(body.encode('ascii'))}{SOH}"
    without_checksum = prefix + body
    checksum = sum(without_checksum.encode("ascii")) % 256
    return f"{without_checksum}10={checksum:03}{SOH}\n"


INVALID_FIX = f"8=FIX.4.4{SOH}9=005{SOH}10=000{SOH}\n"
HEARTBEAT_FIX = fix_message([("35", "0"), ("49", "BUY1"), ("56", "SELL1")])
ORDER_FIX = fix_message(
    [
        ("35", "D"),
        ("49", "BUY1"),
        ("56", "SELL1"),
        ("34", "1"),
        ("52", "20260425-10:00:00.000"),
        ("11", "CL-README-1"),
        ("55", "IBM"),
        ("54", "1"),
        ("60", "20260425-10:00:00.000"),
        ("38", "100"),
        ("40", "2"),
        ("44", "50.00"),
    ]
)
EXEC_FIX = fix_message(
    [
        ("35", "8"),
        ("49", "SELL1"),
        ("56", "BUY1"),
        ("34", "1"),
        ("52", "20260425-10:00:01.000"),
        ("37", "ORD-README-1"),
        ("11", "CL-README-1"),
        ("17", "EX-README-1"),
        ("150", "0"),
        ("39", "0"),
        ("55", "IBM"),
        ("54", "1"),
        ("38", "100"),
        ("32", "0"),
        ("151", "100"),
        ("14", "0"),
        ("6", "0"),
    ]
)


EXAMPLES: tuple[ReadmeExample, ...] = (
    ReadmeExample(
        option="--xml",
        display_command="fixdecoder --xml resources/FIX44.xml --fix=44 --info",
        args=("--xml", "resources/FIX44.xml", "--fix=44", "--info"),
        max_lines=14,
    ),
    ReadmeExample(
        option="--fix",
        display_command="fixdecoder --fix=FIX50SP2 --info",
        args=("--fix=FIX50SP2", "--info"),
        max_lines=14,
    ),
    ReadmeExample(
        option="--info",
        display_command="fixdecoder --info",
        args=("--info",),
        max_lines=14,
    ),
    ReadmeExample(
        option="--message",
        display_command="fixdecoder --fix=44 --message=D --column",
        args=("--fix=44", "--message=D", "--column"),
        max_lines=26,
    ),
    ReadmeExample(
        option="--component",
        display_command="fixdecoder --fix=44 --component=Instrument --column",
        args=("--fix=44", "--component=Instrument", "--column"),
        max_lines=22,
    ),
    ReadmeExample(
        option="--tag",
        display_command="fixdecoder --fix=44 --tag=44 --verbose --column",
        args=("--fix=44", "--tag=44", "--verbose", "--column"),
        max_lines=24,
    ),
    ReadmeExample(
        option="--validate",
        display_command="printf '<invalid FIX>' | fixdecoder --fix=44 --validate --nocounts --colour=no",
        args=("--fix=44", "--validate", "--nocounts", "--colour=no"),
        stdin=INVALID_FIX,
        max_lines=18,
        allow_failure=True,
    ),
    ReadmeExample(
        option="--secret",
        display_command="printf '<FIX log>' | fixdecoder --fix=44 --secret --nocounts --delimiter='|' --colour=no",
        args=("--fix=44", "--secret", "--nocounts", "--delimiter=|", "--colour=no"),
        stdin=HEARTBEAT_FIX,
        max_lines=20,
    ),
    ReadmeExample(
        option="--secret-files",
        display_command="fixdecoder --secret-files target/readme-examples/orders.log",
        args=("--secret-files", "target/readme-examples/orders.log"),
        setup="secret-file",
        max_lines=8,
    ),
    ReadmeExample(
        option="--colour",
        display_command="printf '<FIX log>' | fixdecoder --fix=44 --nocounts --colour=no",
        args=("--fix=44", "--nocounts", "--colour=no"),
        stdin=HEARTBEAT_FIX,
        max_lines=18,
    ),
    ReadmeExample(
        option="--delimiter",
        display_command="printf '<FIX log>' | fixdecoder --fix=44 --nocounts --delimiter=' ' --colour=no",
        args=("--fix=44", "--nocounts", "--delimiter= ", "--colour=no"),
        stdin=HEARTBEAT_FIX,
        max_lines=18,
    ),
    ReadmeExample(
        option="--nocounts",
        display_command="printf '<FIX log>' | fixdecoder --fix=44 --nocounts --colour=no",
        args=("--fix=44", "--nocounts", "--colour=no"),
        stdin=HEARTBEAT_FIX,
        max_lines=18,
    ),
    ReadmeExample(
        option="--summary",
        display_command="printf '<order FIX log>' | fixdecoder --fix=44 --summary --nocounts --paging=never --colour=no",
        args=("--fix=44", "--summary", "--nocounts", "--paging=never", "--colour=no"),
        stdin=ORDER_FIX + EXEC_FIX,
        max_lines=28,
    ),
)


def prepare_secret_file() -> None:
    """Create a disposable input file for the --secret-files README example."""
    EXAMPLE_DIR.mkdir(parents=True, exist_ok=True)
    source = EXAMPLE_DIR / "orders.log"
    secret = EXAMPLE_DIR / "orders.secret.log"
    if secret.exists():
        secret.unlink()
    source.write_text(f"INFO {HEARTBEAT_FIX.rstrip()} tail\n")


def render_usage_section() -> str:
    """Render the generated full usage section."""
    usage = USAGE.read_text().rstrip()
    return "\n".join(
        [
            "<!-- regen-readme:start --section=usage -->",
            "",
            "## Full Usage",
            "",
            "The text below is generated from `resources/messages/usage_en.txt`, the same usage text printed after `fixdecoder --help`.",
            "",
            "```text",
            usage,
            "```",
            "",
            "<!-- regen-readme:end --section=usage -->",
            "",
            "",
        ]
    )


def render_examples_section() -> str:
    """Render generated command examples for the major user-facing options."""
    blocks = [
        "<!-- regen-readme:start --section=examples -->",
        "",
        "## Generated CLI Examples",
        "",
        "These examples are generated by `make regen-readme` using the Java command-line application.",
        "",
    ]
    for example in EXAMPLES:
        blocks.append(render_example_block(example))
    blocks.extend(["<!-- regen-readme:end --section=examples -->", "", ""])
    return "\n".join(blocks)


def render_example_block(example: ReadmeExample) -> str:
    """Run one README example and format its sanitized output as Markdown."""
    output = render_example_output(example)
    body = f"$ {example.display_command}"
    if output:
        body = f"{body}\n{output}"
    return "\n".join(
        [
            f"### `{example.option}`",
            "",
            "```bash",
            body,
            "```",
            "",
        ]
    )


def render_example_output(example: ReadmeExample) -> str:
    """Run one Java CLI example, returning a short sanitized output block."""
    if example.setup == "secret-file":
        prepare_secret_file()

    result = run_java_app(list(example.args), stdin=example.stdin)
    output = f"{result.stdout}{result.stderr}"
    if result.returncode != 0 and not example.allow_failure:
        raise RuntimeError(
            f"README example failed for {example.option}: {example.display_command}\n{output}"
        )
    return limit_lines(sanitise_output(output), example.max_lines)


def sanitise_output(output: str) -> str:
    """Normalize process output so README diffs are stable across machines."""
    output = ANSI_RE.sub("", output)
    output = output.replace(SOH, "|")
    output = output.replace(str(ROOT) + "/", "")
    output = output.replace(str(ROOT), ".")
    output = output.replace(str(Path.home()), "~")
    return "\n".join(line.rstrip() for line in output.rstrip().splitlines())


def limit_lines(output: str, max_lines: int) -> str:
    """Keep generated README examples compact."""
    lines = output.splitlines()
    if len(lines) <= max_lines:
        return "\n".join(lines)
    shown = lines[:max_lines]
    shown.append("...")
    return "\n".join(shown)


def replace_section(markdown: str, section: str, block: str) -> str:
    """Replace a generated README section or insert it before Development."""
    section_re = re.compile(
        rf"<!-- regen-readme:start --section={re.escape(section)} -->\n.*?"
        rf"<!-- regen-readme:end --section={re.escape(section)} -->\n*",
        re.S,
    )
    if section_re.search(markdown):
        return section_re.sub(block, markdown)

    anchor = "\n## Development"
    if anchor in markdown:
        return markdown.replace(anchor, f"\n{block}## Development", 1)
    return f"{markdown.rstrip()}\n\n{block}"


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
