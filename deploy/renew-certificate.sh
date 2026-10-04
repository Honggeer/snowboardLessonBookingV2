#!/bin/sh
set -eu
exec python3 "$(dirname "$0")/renew_certificate.py" "$@"
