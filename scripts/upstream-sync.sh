#!/usr/bin/env bash
#
# Maintain the fork's stable branch in sync with upstream/cmp-rewrite.
#
# Run it from the stable branch:
#   git switch stable
#   ./scripts/upstream-sync.sh
#
# The script never merges feature branches. Features reach stable only through
# an explicit `git merge --no-ff <branch>` (normally from the dev branch).

set -euo pipefail

STABLE_BRANCH="${STABLE_BRANCH:-stable}"
ORIGIN_REMOTE="${ORIGIN_REMOTE:-origin}"
BASE_REMOTE="${BASE_REMOTE:-upstream}"
BASE_BRANCH="${BASE_BRANCH:-cmp-rewrite}"
BASE_REF="${BASE_REMOTE}/${BASE_BRANCH}"

dry_run=false
do_push=true

usage() {
    cat <<'EOF'
Usage: scripts/upstream-sync.sh [options]

Merges upstream/cmp-rewrite into the fork's stable branch.

Options:
  --dry-run    Show what would be merged, without merging or pushing.
  --no-push    Merge locally but do not push to origin.
  --help       Show this help.
EOF
}

for arg in "$@"; do
    case "$arg" in
        --dry-run) dry_run=true ;;
        --no-push) do_push=false ;;
        --help|-h) usage; exit 0 ;;
        *)
            echo "Unknown option: $arg" >&2
            usage >&2
            exit 2
            ;;
    esac
done

summary() {
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
        printf '%s\n' "$*" >>"$GITHUB_STEP_SUMMARY"
    fi
}

fail() {
    echo "ERROR: $*" >&2
    summary "ERROR: $*"
    exit 1
}

current_branch="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$current_branch" != "$STABLE_BRANCH" ]]; then
    fail "current branch is '$current_branch'; switch to '$STABLE_BRANCH' before syncing."
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
    fail "there are uncommitted tracked changes; commit or stash them before syncing."
fi

if ! git remote get-url "$BASE_REMOTE" >/dev/null 2>&1; then
    echo "Adding remote '$BASE_REMOTE' (https://github.com/NuvioMedia/NuvioMobile.git)"
    git remote add "$BASE_REMOTE" https://github.com/NuvioMedia/NuvioMobile.git
fi

echo "Fetching $ORIGIN_REMOTE and $BASE_REMOTE..."
git fetch --prune "$ORIGIN_REMOTE"
git fetch --prune "$BASE_REMOTE"

if ! git rev-parse --verify --quiet "$BASE_REF" >/dev/null; then
    fail "$BASE_REF does not exist after fetching."
fi

read -r ahead behind < <(git rev-list --left-right --count "HEAD...$BASE_REF")
echo "Local: $ahead commit(s) ahead, $behind behind $BASE_REF"

merge_if_needed() {
    local ref="$1"
    local message="$2"

    if git merge-base --is-ancestor "$ref" HEAD; then
        echo "Already included: $ref"
        return 0
    fi

    if [[ "$dry_run" == true ]]; then
        local pending
        pending="$(git rev-list --count "HEAD..$ref")"
        echo "[dry-run] would merge $ref ($pending commit(s))"
        return 0
    fi

    echo "Merging $ref..."
    if ! git merge --no-ff --no-edit -m "$message" "$ref"; then
        local conflicts
        conflicts="$(git diff --name-only --diff-filter=U || true)"
        git merge --abort 2>/dev/null || true
        fail "conflict while merging $ref (${conflicts:-unknown files}). Resolve manually with: git merge $ref"
    fi
}

remote_stable="$ORIGIN_REMOTE/$STABLE_BRANCH"
if git rev-parse --verify --quiet "$remote_stable" >/dev/null; then
    merge_if_needed "$remote_stable" "chore($STABLE_BRANCH): reconcile $remote_stable"
fi

merge_if_needed "$BASE_REF" "chore($STABLE_BRANCH): merge $BASE_REF"

if [[ "$dry_run" == true ]]; then
    echo "[dry-run] no merge or push performed."
    summary "Upstream sync dry-run: $BASE_REF checked."
    exit 0
fi

if [[ "$do_push" == false ]]; then
    echo "Merge complete; push skipped (--no-push)."
    summary "Upstream sync: local merge complete, push skipped."
    exit 0
fi

echo "Pushing $STABLE_BRANCH to $ORIGIN_REMOTE..."
if ! git push "$ORIGIN_REMOTE" "HEAD:$STABLE_BRANCH"; then
    echo "Push rejected; reconciling $remote_stable and retrying once..."
    git fetch "$ORIGIN_REMOTE"
    merge_if_needed "$remote_stable" "chore($STABLE_BRANCH): reconcile $remote_stable after rejected push"
    git push "$ORIGIN_REMOTE" "HEAD:$STABLE_BRANCH"
fi

echo "Done: $STABLE_BRANCH is up to date with $BASE_REF."
summary "Upstream sync: $STABLE_BRANCH updated with $BASE_REF."
