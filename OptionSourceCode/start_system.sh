#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/logs"
RUN_DIR="$BASE_DIR/run"

mkdir -p "$LOG_DIR" "$RUN_DIR"
cd "$BASE_DIR"

start_one() {
  local name="$1"
  local cmd="$2"
  local pid_file="$RUN_DIR/$name.pid"

  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "$name already running (pid $(cat "$pid_file"))."
    return 0
  fi

  nohup bash -c "$cmd" >"$LOG_DIR/$name.log" 2>&1 &
  echo $! >"$pid_file"
  echo "Started $name (pid $(cat "$pid_file"))."
}

if [[ ! -d "$BASE_DIR/bin" ]]; then
  echo "bin/ not found. Run ./build.sh first."
  exit 1
fi

start_one "tm" "java -cp bin transaction.TransactionManagerImpl"
start_one "rm_flights" "java -cp bin transaction.RMManagerFlights"
start_one "rm_hotels" "java -cp bin transaction.RMManagerHotels"
start_one "rm_cars" "java -cp bin transaction.RMManagerCars"
start_one "rm_customers" "java -cp bin transaction.RMManagerCustomers"
start_one "wc" "java -cp bin transaction.WorkflowControllerImpl"

echo "All components started."
echo "Logs: $LOG_DIR"
