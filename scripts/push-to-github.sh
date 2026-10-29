#!/usr/bin/env bash
# Commit this folder and push it to https://github.com/ThirtyFoxMCx00E1/SPIKE (main).
# You need to be logged in: `gh auth login`, or use a personal access token as the password.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="https://github.com/ThirtyFoxMCx00E1/SPIKE.git"

[ -d .git ] || git init -b main
git remote get-url origin >/dev/null 2>&1 || git remote add origin "$REPO"
git add -A
git commit -m "${1:-SPIKE APP: Android + Windows agent (SPK 1.0)}" || echo "nothing new to commit"

# The remote already has a README commit: merge it, keeping our files on conflict.
git fetch origin main || true
git merge origin/main --allow-unrelated-histories -X ours -m "Merge remote README" || true
git push -u origin main

echo "Pushed. To publish a release build:  git tag v1.0.0 && git push origin v1.0.0"
