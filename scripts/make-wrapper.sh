#!/usr/bin/env bash
# One-time: generate the Gradle wrapper (needs a local Gradle 8.x). CI does not need it.
set -euo pipefail
cd "$(dirname "$0")/.."
gradle wrapper --gradle-version 8.10.2
