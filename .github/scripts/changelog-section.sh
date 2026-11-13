#!/usr/bin/env bash
set -euo pipefail

version="${1:?usage: changelog-section.sh <version> [changelog]}"
file="${2:-CHANGELOG.md}"

section="$(tr -d '\r' < "$file" | awk -v header="## [${version}]" '
  /^## \[/ { if (found) exit; found = (index($0, header) == 1); next }
  found && /^\[[^]]+\]: / { next }
  found { print }
')"

section="$(printf '%s\n' "$section" | sed -e :a -e '/^[[:space:]]*$/{$d;N;ba' -e '}' | sed '/./,$!d')"

if [ -z "$section" ]; then
  echo "No changelog entry found for version ${version} in ${file}" >&2
  exit 1
fi

printf '%s\n' "$section"
