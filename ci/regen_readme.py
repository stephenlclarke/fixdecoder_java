#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools
"""Regenerate README usage text and Java command-line output examples."""

from __future__ import annotations

import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

from java_generator_common import ROOT, ensure_java_app, run_java_app

README = ROOT / "README.md"
EXAMPLE_DIR = ROOT / "target" / "readme-examples"
ANSI_RE = re.compile(r"\x1b\[[0-9;?]*[ -/]*[@-~]")
LONG_OPTION_RE = re.compile(r"(?<![\w-])--([A-Za-z][A-Za-z0-9-]*)")
MESSAGE_CODE_NAME_RE = re.compile(r"(?m)^\s*([A-Za-z0-9]{1,4})\s*:\s*([A-Za-z][A-Za-z0-9_]*)")
MESSAGE_NAME_CODE_RE = re.compile(r"(?m)^\s*([A-Za-z][A-Za-z0-9_]*)\s+\(([A-Za-z0-9]{1,4})\)")
COMPONENT_NAME_RE = re.compile(r"\b([A-Z][A-Za-z0-9_]*(?:Grp|Data|Instructions|Parties|Instrument|Trailer|Header|Hop)?)\b")
TAG_FIELD_RE = re.compile(r"(?m)^\s*([0-9]+)\s*:\s*([A-Za-z][A-Za-z0-9_]*)")
SOH = "\x01"
GROUP_TAG_CANDIDATES = ("453", "78", "802", "539", "804", "268")
PREFERRED_COMPONENTS = ("PreAllocGrp", "Parties", "Instrument")


@dataclass(frozen=True)
class BuildExample:
    """A deterministic shell transcript for the generated Build it section."""

    display_command: str
    output: str


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


@dataclass(frozen=True)
class CapabilitySnapshot:
    """A generated summary of this implementation's discoverable CLI surface."""

    help_text: str
    options: frozenset[str]
    message_code: str
    message_name: str
    component_name: str
    group_tag: str
    group_name: str


def main() -> int:
    """Regenerate generated README sections from the current Java CLI."""
    if not README.exists():
        print(f"README not found: {README}", file=sys.stderr)
        return 1

    ensure_java_app()
    capabilities = discover_capabilities()
    original = README.read_text()
    updated = replace_section(original, "build-examples", render_build_examples_section())
    updated = replace_section(updated, "usage", render_usage_section(capabilities))
    updated = replace_or_move_section(
        updated,
        "capabilities",
        render_capability_section(capabilities),
        "\n<!-- regen-readme:start --section=examples -->",
    )
    updated = replace_section(updated, "examples", render_examples_section(capabilities))
    README.write_text(updated)
    print(
        f"Updated {README.relative_to(ROOT)} with generated build, usage, "
        "capability discovery, and CLI examples"
    )
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


def discover_capabilities() -> CapabilitySnapshot:
    """Discover supported options and representative dictionary entries from the Java CLI."""
    help_text = run_required_java_app(["--help"])
    options = frozenset(f"--{option}" for option in sorted(set(LONG_OPTION_RE.findall(help_text))))
    colour_args = ["--colour=no"] if "--colour" in options else []

    message_output = run_required_java_app(["--fix=44", "--message", "--column", *colour_args])
    message_code, message_name = choose_message(parse_messages(message_output))

    component_output = run_required_java_app(["--fix=44", "--component", "--column", *colour_args])
    component_name = choose_component(parse_components(component_output))

    group_tag, group_name = choose_group_tag(options, colour_args)
    return CapabilitySnapshot(
        help_text=sanitise_output(help_text),
        options=options,
        message_code=message_code,
        message_name=message_name,
        component_name=component_name,
        group_tag=group_tag,
        group_name=group_name,
    )


def run_required_java_app(args: list[str], stdin: str | None = None) -> str:
    """Run the Java app and fail generation if discovery cannot complete."""
    result = run_java_app(args, stdin=stdin)
    output = f"{result.stdout}{result.stderr}"
    if result.returncode != 0:
        raise RuntimeError(f"Java README discovery failed for {' '.join(args)}\n{output}")
    return output


def parse_messages(output: str) -> list[tuple[str, str]]:
    """Parse message listings regardless of whether they print code-first or name-first."""
    clean = sanitise_output(output)
    found: list[tuple[str, str]] = []
    found.extend((match.group(1), match.group(2)) for match in MESSAGE_CODE_NAME_RE.finditer(clean))
    found.extend((match.group(2), match.group(1)) for match in MESSAGE_NAME_CODE_RE.finditer(clean))
    return dedupe_pairs(found)


def parse_components(output: str) -> list[str]:
    """Parse component listings from column or line-oriented output."""
    clean = sanitise_output(output)
    ignored = {"Session", "Admin", "Business", "Order", "Flow", "Pricing"}
    names = [
        match.group(1)
        for match in COMPONENT_NAME_RE.finditer(clean)
        if match.group(1) not in ignored and len(match.group(1)) > 2
    ]
    return dedupe_names(names)


def choose_message(messages: list[tuple[str, str]]) -> tuple[str, str]:
    """Choose a stable sample message, preferring NewOrderSingle when available."""
    for code, name in messages:
        if code == "D" or name == "NewOrderSingle":
            return code, name
    if messages:
        return messages[0]
    return "D", "NewOrderSingle"


def choose_component(components: list[str]) -> str:
    """Choose a stable sample component, preferring a repeating-group-heavy one."""
    for preferred in PREFERRED_COMPONENTS:
        if preferred in components:
            return preferred
    return components[0] if components else "Instrument"


def choose_group_tag(options: frozenset[str], colour_args: list[str]) -> tuple[str, str]:
    """Find a representative repeating-group NumInGroup tag from the selected dictionary."""
    if "--tag" not in options:
        return "453", "NoPartyIDs"
    for tag in GROUP_TAG_CANDIDATES:
        output = run_required_java_app(["--fix=44", f"--tag={tag}", "--verbose", "--column", *colour_args])
        clean = sanitise_output(output)
        match = TAG_FIELD_RE.search(clean)
        if match and ("NUMINGROUP" in clean or match.group(2).startswith("No")):
            return match.group(1), match.group(2)
    return "453", "NoPartyIDs"


def dedupe_pairs(pairs: list[tuple[str, str]]) -> list[tuple[str, str]]:
    """Preserve first-seen message pairs while removing duplicates."""
    seen: set[tuple[str, str]] = set()
    result: list[tuple[str, str]] = []
    for pair in pairs:
        if pair in seen:
            continue
        seen.add(pair)
        result.append(pair)
    return result


def dedupe_names(names: list[str]) -> list[str]:
    """Preserve first-seen component names while removing duplicates."""
    seen: set[str] = set()
    result: list[str] = []
    for name in names:
        if name in seen:
            continue
        seen.add(name)
        result.append(name)
    return result


def build_readme_examples(capabilities: CapabilitySnapshot) -> tuple[ReadmeExample, ...]:
    """Build README examples from the options this binary actually supports."""
    examples: list[ReadmeExample] = []
    colour_args = ("--colour=no",) if "--colour" in capabilities.options else ()
    quiet_args = ("--nocounts",) if "--nocounts" in capabilities.options else ()

    def add_if_supported(option: str, example: ReadmeExample) -> None:
        if option in capabilities.options:
            examples.append(example)

    add_if_supported(
        "--xml",
        ReadmeExample(
            option="--xml",
            display_command="fixdecoder --xml resources/FIX44.xml --fix=44 --info",
            args=("--xml", "resources/FIX44.xml", "--fix=44", "--info"),
            max_lines=14,
        ),
    )
    add_if_supported(
        "--fix",
        ReadmeExample(
            option="--fix",
            display_command="fixdecoder --fix=FIX50SP2 --info",
            args=("--fix=FIX50SP2", "--info"),
            max_lines=14,
        ),
    )
    add_if_supported(
        "--info",
        ReadmeExample(
            option="--info",
            display_command="fixdecoder --info",
            args=("--info",),
            max_lines=14,
        ),
    )
    add_if_supported(
        "--message",
        ReadmeExample(
            option="--message",
            display_command=f"fixdecoder --fix=44 --message={capabilities.message_code} --column",
            args=("--fix=44", f"--message={capabilities.message_code}", "--column", *colour_args),
            max_lines=26,
        ),
    )
    add_if_supported(
        "--component",
        ReadmeExample(
            option="--component",
            display_command=f"fixdecoder --fix=44 --component={capabilities.component_name} --column",
            args=("--fix=44", f"--component={capabilities.component_name}", "--column", *colour_args),
            max_lines=22,
        ),
    )
    add_if_supported(
        "--tag",
        ReadmeExample(
            option="--tag",
            display_command=f"fixdecoder --fix=44 --tag={capabilities.group_tag} --verbose --column",
            args=("--fix=44", f"--tag={capabilities.group_tag}", "--verbose", "--column", *colour_args),
            max_lines=24,
        ),
    )
    add_if_supported(
        "--validate",
        ReadmeExample(
            option="--validate",
            display_command="printf '<invalid FIX>' | fixdecoder --fix=44 --validate --nocounts --colour=no",
            args=("--fix=44", "--validate", *quiet_args, *colour_args),
            stdin=INVALID_FIX,
            max_lines=18,
            allow_failure=True,
        ),
    )
    add_if_supported(
        "--secret",
        ReadmeExample(
            option="--secret",
            display_command="printf '<FIX log>' | fixdecoder --fix=44 --secret --nocounts --delimiter='|' --colour=no",
            args=("--fix=44", "--secret", *quiet_args, "--delimiter=|", *colour_args),
            stdin=HEARTBEAT_FIX,
            max_lines=20,
        ),
    )
    add_if_supported(
        "--secret-files",
        ReadmeExample(
            option="--secret-files",
            display_command="fixdecoder --secret-files target/readme-examples/orders.log",
            args=("--secret-files", "target/readme-examples/orders.log"),
            setup="secret-file",
            max_lines=8,
        ),
    )
    add_if_supported(
        "--colour",
        ReadmeExample(
            option="--colour",
            display_command="printf '<FIX log>' | fixdecoder --fix=44 --nocounts --colour=no",
            args=("--fix=44", *quiet_args, *colour_args),
            stdin=HEARTBEAT_FIX,
            max_lines=18,
        ),
    )
    add_if_supported(
        "--delimiter",
        ReadmeExample(
            option="--delimiter",
            display_command="printf '<FIX log>' | fixdecoder --fix=44 --nocounts --delimiter=' ' --colour=no",
            args=("--fix=44", *quiet_args, "--delimiter= ", *colour_args),
            stdin=HEARTBEAT_FIX,
            max_lines=18,
        ),
    )
    add_if_supported(
        "--nocounts",
        ReadmeExample(
            option="--nocounts",
            display_command="printf '<FIX log>' | fixdecoder --fix=44 --nocounts --colour=no",
            args=("--fix=44", *quiet_args, *colour_args),
            stdin=HEARTBEAT_FIX,
            max_lines=18,
        ),
    )
    add_if_supported(
        "--summary",
        ReadmeExample(
            option="--summary",
            display_command="printf '<order FIX log>' | fixdecoder --fix=44 --summary --nocounts --paging=no --colour=no",
            args=("--fix=44", "--summary", *quiet_args, "--paging=no", *colour_args),
            stdin=ORDER_FIX + EXEC_FIX,
            max_lines=28,
        ),
    )
    return tuple(examples)


def prepare_secret_file() -> None:
    """Create a disposable input file for the --secret-files README example."""
    EXAMPLE_DIR.mkdir(parents=True, exist_ok=True)
    source = EXAMPLE_DIR / "orders.log"
    secret = EXAMPLE_DIR / "orders.secret.log"
    if secret.exists():
        secret.unlink()
    source.write_text(f"INFO {HEARTBEAT_FIX.rstrip()} tail\n")


def render_usage_section(capabilities: CapabilitySnapshot) -> str:
    """Render the generated full usage section."""
    usage = capabilities.help_text.rstrip()
    return "\n".join(
        [
            "<!-- regen-readme:start --section=usage -->",
            "",
            "## Full Usage",
            "",
            "The text below is generated by running this implementation's `fixdecoder --help`.",
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


def render_capability_section(capabilities: CapabilitySnapshot) -> str:
    """Render a generated snapshot of discovered options and dictionary samples."""
    options = ", ".join(f"`{option}`" for option in sorted(capabilities.options))
    message_output = limit_lines(
        sanitise_output(
            run_required_java_app(
                ["--fix=44", f"--message={capabilities.message_code}", "--column", *colour_args(capabilities)]
            )
        ),
        18,
    )
    component_output = limit_lines(
        sanitise_output(
            run_required_java_app(
                ["--fix=44", f"--component={capabilities.component_name}", "--column", *colour_args(capabilities)]
            )
        ),
        16,
    )
    group_output = limit_lines(
        sanitise_output(
            run_required_java_app(
                ["--fix=44", f"--tag={capabilities.group_tag}", "--verbose", "--column", *colour_args(capabilities)]
            )
        ),
        12,
    )
    return "\n".join(
        [
            "<!-- regen-readme:start --section=capabilities -->",
            "",
            "## Generated Capability Snapshot",
            "",
            "This snapshot is generated by `make regen-readme` by running this implementation's binary and reflects the options and dictionary surface currently available in this repository.",
            "",
            f"- Supported long options: {options}",
            f"- Sample message discovered from the dictionary: `{capabilities.message_name} ({capabilities.message_code})`",
            f"- Sample component discovered from the dictionary: `{capabilities.component_name}`",
            f"- Sample repeating group tag discovered from the dictionary: `{capabilities.group_name} ({capabilities.group_tag})`",
            "",
            "```bash",
            f"$ fixdecoder --fix=44 --message={capabilities.message_code} --column",
            message_output,
            "```",
            "",
            "```bash",
            f"$ fixdecoder --fix=44 --component={capabilities.component_name} --column",
            component_output,
            "```",
            "",
            "```bash",
            f"$ fixdecoder --fix=44 --tag={capabilities.group_tag} --verbose --column",
            group_output,
            "```",
            "",
            "<!-- regen-readme:end --section=capabilities -->",
            "",
            "",
        ]
    )


def colour_args(capabilities: CapabilitySnapshot) -> tuple[str, ...]:
    """Return the no-colour option only for implementations that support it."""
    return ("--colour=no",) if "--colour" in capabilities.options else ()


def render_build_examples_section() -> str:
    """Render generated build examples using the Rust README structure."""
    examples = [
        BuildExample(
            "bash --version",
            "\n".join(render_shell_command(("bash", "--version")).splitlines()[:3]),
        ),
        BuildExample(
            "java -version",
            "\n".join(render_shell_command(("java", "-version")).splitlines()[:3]),
        ),
        BuildExample(
            "git clone git@github.com:stephenlclarke/fixdecoder_java.git",
            "Cloning into 'fixdecoder_java'...\n...\n❯ cd fixdecoder_java",
        ),
        BuildExample(
            "make clean build scan coverage build-release",
            "\n".join(
                [
                    "",
                    "[INFO] Compiling Java sources with lint warnings as errors",
                    "[INFO] Running unit tests and JaCoCo coverage checks",
                    "[INFO] Building shaded runnable jar: target/fixdecoder-java-0.3.0.jar",
                    "[INFO] BUILD SUCCESS",
                ]
            ),
        ),
        BuildExample(
            "make build-release",
            "\n".join(
                [
                    "",
                    "[INFO] Building shaded runnable jar: target/fixdecoder-java-0.3.0.jar",
                    "[INFO] BUILD SUCCESS",
                ]
            ),
        ),
        BuildExample(
            "java -jar target/fixdecoder-java-0.3.0.jar --version",
            render_shell_command(("java", "-jar", "target/fixdecoder-java-0.3.0.jar", "--version")),
        ),
        BuildExample(
            "scripts/fixdecoder --version",
            render_shell_command(("scripts/fixdecoder", "--version")),
        ),
    ]

    return "\n".join(
        [
            "<!-- regen-readme:start --section=build-examples -->",
            "",
            "## Build it",
            "",
            "Build it from source. This requires `bash`, Java 21+, and the checked-in Maven wrapper.",
            "",
            "```bash",
            format_prompted_output(examples[0]),
            "```",
            "",
            "```bash",
            format_prompted_output(examples[1]),
            "```",
            "",
            "Clone the git repo.",
            "",
            "```bash",
            format_prompted_output(examples[2]),
            "```",
            "",
            "Then build it. Local builds compile the shaded jar, run scan-friendly compilation, and produce coverage.",
            "",
            "```bash",
            format_prompted_output(examples[3]),
            "```",
            "",
            "Build only the release-oriented runnable jar.",
            "",
            "```bash",
            format_prompted_output(examples[4]),
            "```",
            "",
            "Run it (from the release build) and check the version details:",
            "",
            "```bash",
            format_prompted_output(examples[5]),
            "```",
            "",
            "Run the same build through the source-checkout wrapper:",
            "",
            "```bash",
            format_prompted_output(examples[6]),
            "```",
            "",
            "<!-- regen-readme:end --section=build-examples -->",
            "",
            "",
        ]
    )


def render_examples_section(capabilities: CapabilitySnapshot) -> str:
    """Render generated command examples for the major user-facing options."""
    blocks = [
        "<!-- regen-readme:start --section=examples -->",
        "",
        "## Generated CLI Examples",
        "",
        "These examples are generated by `make regen-readme` using the Java command-line application.",
        "",
    ]
    for example in build_readme_examples(capabilities):
        blocks.append(render_example_block(example))
    blocks.extend(["<!-- regen-readme:end --section=examples -->", "", ""])
    return "\n".join(blocks)


def render_shell_command(command: tuple[str, ...]) -> str:
    """Run a local command and return sanitized stdout plus stderr."""
    result = subprocess.run(
        list(command),
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=True,
    )
    return sanitise_output(result.stdout)


def format_prompted_output(example: BuildExample) -> str:
    """Format a shell command with the README prompt marker."""
    body = f"❯ {example.display_command}"
    if example.output:
        body = f"{body}\n{example.output}"
    return body


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
        return section_re.sub(lambda _: block, markdown)

    anchor = "\n## Development"
    if anchor in markdown:
        return markdown.replace(anchor, f"\n{block}## Development", 1)
    return f"{markdown.rstrip()}\n\n{block}"


def replace_or_move_section(markdown: str, section: str, block: str, anchor: str) -> str:
    """Replace a generated section and move it before the requested anchor."""
    section_re = re.compile(
        rf"<!-- regen-readme:start --section={re.escape(section)} -->\n.*?"
        rf"<!-- regen-readme:end --section={re.escape(section)} -->\n*",
        re.S,
    )
    without_existing = section_re.sub(lambda _: "", markdown)
    if anchor in without_existing:
        return without_existing.replace(anchor, f"\n{block}{anchor.lstrip()}", 1)
    return f"{without_existing.rstrip()}\n\n{block}"


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
