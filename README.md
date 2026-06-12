![repo logo](docs/repo-logo.png)
![repo title](docs/repo-title.png)

---

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=bugs)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=coverage)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=stephenlclarke_fixdecoder_java&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=stephenlclarke_fixdecoder_java)
![Repo Visitors](https://visitor-badge.laobi.icu/badge?page_id=stephenlclarke.fixdecoder_java)

---

# Steve's FIX Decoder / logfile prettify utility

This repository is the Java implementation of [fixdecoder_rs](https://github.com/stephenlclarke/fixdecoder_rs). It keeps the command-line surface, embedded FIX dictionaries, sample corpus, documentation assets, SonarCloud analysis, and CI/CD flow aligned with the Rust version while using an object-oriented Java design.

The implementation uses immutable dictionary metadata, reusable mutable FIX message buffers, streaming NIO file processing, concurrent multi-file decoding, and focused unit tests with JaCoCo coverage.

## What is it

fixdecoder is a FIX-aware tail-like tool and dictionary explorer. It reads from stdin or multiple log files, detects and prettifies FIX messages in stream, and fits naturally into pipelines. Each highlighted message is followed by a detailed tag breakdown using the correct dictionary for BeginString (8) or the selected default (`--fix`, default `44`). It can validate on the fly (`--validate`), obfuscate sensitive fields (`--secret`, `--secret-files`), and inspect dictionary messages, components, and tags.

## Quick start

```bash
make build

# Stream and prettify stdin
cat fixlog.txt | java -jar target/fixdecoder-java-0.3.0.jar

# Stream with validation
cat fixlog.txt | java -jar target/fixdecoder-java-0.3.0.jar --validate
```

## Running the utility

```text
fixdecoder [[--fix=44] [--xml=FILE --xml=FILE2 ...]] [--info]
fixdecoder [[--fix=44] [--xml=FILE --xml=FILE2 ...]] [--message[=NAME|MSGTYPE] [--verbose] [--column] [--header] [--trailer]]
fixdecoder [[--fix=44] [--xml=FILE --xml=FILE2 ...]] [--tag[=TAG] [--verbose] [--column]]
fixdecoder [[--fix=44] [--xml=FILE --xml=FILE2 ...]] [--component[=NAME] [--verbose] [--column]]
fixdecoder [--xml=FILE --xml=FILE2 ...] [--validate] [--colour=yes|no|auto]
           [--style=STYLE] [--plain] [--number] [--paging=auto|never|always]
           [--pager=CMD] [--nowrap] [--nocounts] [--secret] [--summary]
           [--follow] [--fix=VER] [--delimiter=CHAR] [file1.log file2.log ...]
```

Important options:

- Dictionaries: `--xml`, `--fix`, `--info`, `--message`, `--component`, `--tag`
- Output/layout: `--column`, `--verbose`, `--header`, `--trailer`, `--colour`, `--delimiter`
- Bat-style viewing compatibility flags: `--style`, `--plain`, `--number`, `--paging`, `--pager`, `--nowrap`
- Processing modes: `--follow`, `--validate`, `--secret`, `--secret-files`, `--summary`, `--nocounts`

## Development

The local workflow uses Java 21 and Maven.

```bash
./mvnw verify
make build
make test
make coverage
make sonar
```

`make deploy` is intentionally omitted: releases are produced by the GitHub Actions tag workflow rather than a local deployment target.

## Migrated assets

The Java repo carries over the Rust repo's documentation images used by this project, generated examples, QuickFIX license notice, imported sample logs, icon resources, and dictionary XML files under `resources/` and `src/main/resources/`.

## License

Project source is released under the GNU Affero General Public License v3.0 only (`AGPL-3.0-only`). Maintained source files carry SPDX headers matching the Rust implementation:

```text
SPDX-License-Identifier: AGPL-3.0-only
SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools
```

The embedded QuickFIX FIX XML specifications remain under the BSD 2-Clause “Simplified” License. That permissive license is compatible with the project AGPL-3.0-only licensing as long as the QuickFIX notice, conditions, and disclaimer are preserved; see `NOTICE.md` for the retained attribution text.
