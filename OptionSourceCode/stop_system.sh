#!/usr/bin/env bash
set -euo pipefail
rm -r /home/zhaoxiangyu/Ans/Code/data
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$BASE_DIR/run"

cd "$BASE_DIR"

stop_one() {
  local name="$1"
  local pid_file="$RUN_DIR/$name.pid"

  if [[ ! -f "$pid_file" ]]; then
    echo "$name not running (no pid file)."
    return 0
  fi

  local pid
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    for _ in {1..10}; do
      if kill -0 "$pid" 2>/dev/null; then
        sleep 0.2
      else
        break
      fi
    done
    if kill -0 "$pid" 2>/dev/null; then
      echo "Force killing $name (pid $pid)."
      kill -9 "$pid"
    else
      echo "Stopped $name (pid $pid)."
    fi
  else
    echo "$name already stopped (pid $pid not running)."
  fi
  rm -f "$pid_file"
}

stop_one "wc"
stop_one "rm_customers"
stop_one "rm_cars"
stop_one "rm_hotels"
stop_one "rm_flights"
stop_one "tm"

echo "All components stopped."
