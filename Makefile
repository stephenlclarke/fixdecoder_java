SHELL := /bin/bash
JAVA_HOME ?= /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
PATH := $(JAVA_HOME)/bin:$(PATH)
MVN ?= ./mvnw
JAR := target/fixdecoder-java-0.3.0.jar

.PHONY: all clean run build build-release test scan coverage sonar help

all: scan coverage

run: build
	@java -jar $(JAR) --help

build:
	@$(MVN) -q -DskipTests package

build-release:
	@$(MVN) -q -DskipTests -P release package

test:
	@$(MVN) test

scan:
	@$(MVN) -q -DskipTests compile

coverage:
	@$(MVN) verify

sonar:
	@bash -lc 'source ci/ci_helper.sh && ensure_sonar_token && ensure_sonar_scanner && $(MVN) verify sonar:sonar'

clean:
	@$(MVN) clean

help:
	@printf '%s\n' \
	  'Available targets:' \
	  '  all            -> run scan and coverage' \
	  '  run            -> build and print fixdecoder help' \
	  '  build          -> compile and package the shaded jar' \
	  '  build-release  -> release-oriented package build' \
	  '  test           -> run unit tests' \
	  '  scan           -> compile with Java lint warnings as errors' \
	  '  coverage       -> run tests and generate JaCoCo coverage' \
	  '  sonar          -> run SonarCloud analysis' \
	  '  clean          -> remove Maven build outputs'
