#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$BASE_DIR"

mkdir -p bin
javac -d bin -cp src src/lockmgr/*.java src/transaction/*.java
echo "Build complete: $BASE_DIR/bin"
