#!/usr/bin/env bash
# GBL-013 US-5 (wave 2) — CI contract for plantuml-gradle.
# The functional/BDD shards (pgvector) and coverage merge stay on the host
# adapters; this contract covers the unit + lint gate.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/plantuml-plugin"

job="${1:-}"
# GBL-014 — publication channel (jitpack default until the Sonatype reset).
channel="${CHANNEL:-jitpack}"
case "$job" in
  fast|test)
    ./gradlew test ktlintCheck validatePlugins verifyChannel -Pchannel="$channel" --no-daemon --console=plain
    ;;
  *)
    printf 'unsupported job for plantuml-gradle: %s\n' "${job:-<none>}" >&2
    printf 'supported: fast | test\n' >&2
    exit 2
    ;;
esac
