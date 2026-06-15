# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

SHELL := /bin/bash
JAVA_HOME ?= /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
PATH := $(JAVA_HOME)/bin:$(PATH)
MVN ?= ./mvnw
JAR := target/fixdecoder-java-0.3.0.jar

.PHONY: all clean run build build-release test scan coverage sonar appendix-d-samples repeating-group-samples regen-example-readmes regen-readme help

all: scan coverage

run: build
	@scripts/fixdecoder --help

build:
	@$(MVN) -q -DskipTests package

build-release:
	@$(MVN) -q -DskipTests -P release package

test:
	@$(MVN) test

scan:
	@$(MVN) -q -DskipTests compile

coverage:
	@$(MVN) verify -Pcoverage

sonar:
	@bash -lc 'source ci/ci_helper.sh && ensure_sonar_token && ensure_sonar_scanner && $(MVN) verify -Pcoverage org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar'

appendix-d-samples:
	@python3 ci/generate_appendix_d_samples.py

repeating-group-samples:
	@python3 ci/generate_repeating_group_samples.py

regen-example-readmes:
	@python3 ci/regen_example_readmes.py

regen-readme:
	@python3 ci/regen_readme.py

clean:
	@$(MVN) clean

help:
	@printf '%s\n' \
	  'Available targets:' \
	  '  all            -> run scan and coverage' \
	  '  run            -> build and print fixdecoder help via the launcher' \
	  '  build          -> compile and package the shaded jar' \
	  '  build-release  -> release-oriented package build' \
	  '  test           -> run unit tests' \
	  '  scan           -> compile with Java lint warnings as errors' \
	  '  coverage       -> run tests and generate JaCoCo coverage' \
	  '  sonar          -> run SonarCloud analysis' \
	  '  appendix-d-samples       -> regenerate Appendix D sample corpus' \
	  '  repeating-group-samples  -> regenerate repeating-group sample corpus' \
	  '  regen-example-readmes    -> regenerate example README pretty-print output' \
	  '  regen-readme             -> regenerate root README usage and CLI examples' \
	  '  clean          -> remove Maven build outputs'
