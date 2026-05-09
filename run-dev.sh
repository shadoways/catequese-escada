#!/usr/bin/env bash
set -euo pipefail

echo "⚠️  [DEPRECATED] use ./run.sh --dev"

exec "$(dirname "$0")/run.sh" --dev "$@"

