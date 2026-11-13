#!/usr/bin/env bash

set -euo pipefail

version_file="${VERSION_FILE:-iosApp/Configuration/Version.xcconfig}"
target_ref="${1:-HEAD}"
tag_suffix="${TAG_SUFFIX:-}"

if ! git cat-file -e "${target_ref}^{commit}" 2>/dev/null; then
    echo "Unknown release target: ${target_ref}" >&2
    exit 1
fi

read_version() {
    local commit="$1"
    git show "${commit}:${version_file}" \
        | sed -nE 's/^[[:space:]]*MARKETING_VERSION[[:space:]]*=[[:space:]]*([^[:space:]#]+).*$/\1/p' \
        | head -n 1
}

current_version=""
current_bump=""
previous_version=""
previous_bump=""

while IFS= read -r commit; do
    version="$(read_version "$commit")"
    [[ -n "$version" ]] || continue

    if [[ -z "$current_version" ]]; then
        current_version="$version"
        current_bump="$commit"
    elif [[ "$version" != "$current_version" ]]; then
        previous_version="$version"
        previous_bump="$commit"
        break
    fi
done < <(git log "$target_ref" --format='%H' -- "$version_file")

if [[ -z "$current_bump" || -z "$previous_bump" ]]; then
    echo "Could not find two distinct version bumps in ${version_file}." >&2
    exit 1
fi

if [[ ! "$current_version" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
    echo "Invalid release version: ${current_version}" >&2
    exit 1
fi

if [[ ! "$tag_suffix" =~ ^[0-9A-Za-z._-]*$ ]]; then
    echo "Invalid tag suffix: ${tag_suffix}" >&2
    exit 1
fi

if [[ -n "$tag_suffix" && "$tag_suffix" != -* ]]; then
    tag_suffix="-${tag_suffix}"
fi

printf 'version=%s\n' "$current_version"
printf 'tag=%s%s-beta\n' "${current_version%-beta}" "${tag_suffix}"
printf 'release_commit=%s\n' "$(git rev-parse "${target_ref}^{commit}")"
printf 'current_bump=%s\n' "$current_bump"
printf 'previous_version=%s\n' "$previous_version"
printf 'previous_bump=%s\n' "$previous_bump"
