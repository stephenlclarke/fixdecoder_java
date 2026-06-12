#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools
# Shared CI helper for the Java implementation.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

function log() {
  printf '\n\033[1;32m%s\033[0m\n' "$1"
}

function warn() {
  printf '\n\033[38;5;214m%s\033[0m\n' "$1"
}

function ensure_sonar_token() {
  if [[ -n "${SONAR_TOKEN:-}" ]]; then
    return
  fi
  local token_file="${HOME}/.secrets/SONAR_TOKEN"
  if [[ -f "${token_file}" ]]; then
    SONAR_TOKEN="$(<"${token_file}")"
    export SONAR_TOKEN
    log ">> Loaded SONAR_TOKEN from ${token_file}"
    return
  fi
  warn "SONAR_TOKEN is not set and ${token_file} was not found."
  return 1
}

function ensure_sonar_scanner() {
  if command -v sonar-scanner >/dev/null 2>&1; then
    return
  fi
  log ">> sonar-scanner is not on PATH; Maven sonar:sonar will download the scanner engine."
}

function download_fix_specs() {
  log ">> Ensuring FIX XML specs are present"
  local resources_dir="${ROOT_DIR}/src/main/resources"
  mkdir -p "${resources_dir}"
  local specs=(
    "FIX40.xml"
    "FIX41.xml"
    "FIX42.xml"
    "FIX43.xml"
    "FIX44.xml"
    "FIX50.xml"
    "FIX50SP1.xml"
    "FIX50SP2.xml"
    "FIXT11.xml"
  )
  for spec in "${specs[@]}"; do
    local dest="${resources_dir}/${spec}"
    local url="https://raw.githubusercontent.com/quickfix/quickfix/master/spec/${spec}"
    if [[ -f "${dest}" ]]; then
      continue
    fi
    log "   downloading ${spec}"
    curl -fsSL -o "${dest}" "${url}"
  done
}
