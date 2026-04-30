#!/usr/bin/env bash
set -euo pipefail

echo "⚠️  [DEPRECATED] use ./run.sh --dev"

if [ $# -gt 0 ] && [ -z "${DEV_DB_PASSWORD:-}" ]; then
  export DEV_DB_PASSWORD="$1"
  shift
fi

exec "$(dirname "$0")/run.sh" --dev "$@"

