#!/usr/bin/env bash
# DEV-006, technical-design §2.6: openapi.yaml must equal what gen_openapi.py generates from api-spec.md,
# and must pass Redocly lint (errors fail, warnings are reported). Run from the repo root:
#   bash scripts/check-openapi.sh [stamp-file]
# The optional stamp file is written on success (Gradle task `checkOpenApi`).
set -euo pipefail

cd "$(dirname "$0")/.."

stamp="${1:-}"
spec="docs/api/api-spec.md"
yaml="docs/api/openapi.yaml"
redocly="@redocly/cli@2.69.0"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

PYTHONDONTWRITEBYTECODE=1 python3 docs/api/tools/gen_openapi.py "$spec" "$tmp/openapi.yaml" >/dev/null
if ! diff -u "$yaml" "$tmp/openapi.yaml" >"$tmp/openapi.diff"; then
    head -n 60 "$tmp/openapi.diff" >&2
    echo "" >&2
    echo "$yaml is out of date with $spec. Never edit it by hand; regenerate it:" >&2
    echo "  python3 docs/api/tools/gen_openapi.py $spec $yaml" >&2
    exit 1
fi
echo "$yaml matches $spec"

npx --yes "$redocly" lint "$yaml"

if [[ -n "$stamp" ]]; then
    mkdir -p "$(dirname "$stamp")"
    echo "ok" >"$stamp"
fi
