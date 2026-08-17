#!/usr/bin/env bash
# Loads .env into this process only, then starts the API.
# Nothing here touches your shell profile or your system.
set -euo pipefail

if [ ! -f .env ]; then
  echo "No .env found. Run:  cp .env.example .env   then fill it in."
  exit 1
fi

set -a          # everything defined from here on is exported to child processes
# shellcheck disable=SC1091
source .env     # read the file, define each KEY=VALUE
set +a          # stop auto-exporting

mvn spring-boot:run
