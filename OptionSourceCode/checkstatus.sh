#!/usr/bin/env bash
# set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$BASE_DIR/run"

cd "$BASE_DIR"

check_one() {
  local name="$1"
  local pid_file="$RUN_DIR/$name.pid"

  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "$name running (pid $(cat "$pid_file"))."
    return 0
  fi
  echo "$name NOT running."
  return 1
}

missing=0
check_one "tm" || missing=1
check_one "rm_flights" || missing=1
check_one "rm_hotels" || missing=1
check_one "rm_cars" || missing=1
check_one "rm_customers" || missing=1
check_one "wc" || missing=1

if [[ "$missing" -ne 0 ]]; then
  echo "One or more components are not running."
  exit 1
fi

if [[ ! -d "$BASE_DIR/bin" ]]; then
  echo "bin/ not found. Run ./build.sh first."
  exit 1
fi

echo "Running client check..."
java -cp bin transaction.Client case6
echo "Checkstatus finished."
