#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/.. && pwd )"

cd "$ROOT_DIR"

echo "[pre-push] Formatting Scala sources"
sbt scalafmtAll

echo "[pre-push] Running backend tests"
sbt test

echo "[pre-push] Installing UI dependencies"
npm --prefix ui ci

echo "[pre-push] Linting Angular sources"
npm --prefix ui run lint

echo "[pre-push] Building Angular assets (prod mode)"
ANGULAR_MODE=prod sbt uiBuild

echo "[pre-push] Done"
