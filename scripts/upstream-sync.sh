#!/usr/bin/env bash
#
# Keep the fork's release channels in sync with their upstream bases:
#
#   main -> upstream/main          (stable channel)
#   dev  -> upstream/cmp-rewrite   (beta channel, the upstream default)
#
# Run it from the branch you want to sync:
#   git switch main
#   ./scripts/upstream-sync.sh
#
#   git switch dev
#   ./scripts/upstream-sync.sh
#
# The script never merges feature branches between channels. Beta features reach
# main through an explicit cherry-pick, never through a `git merge dev`.

set -euo pipefail

STABLE_BRANCH="${STABLE_BRANCH:-main}"
BETA_BRANCH="${BETA_BRANCH:-dev}"
ORIGIN_REMOTE="${ORIGIN_REMOTE:-origin}"
BASE_REMOTE="${BASE_REMOTE:-upstream}"
UPSTREAM_STABLE_BRANCH="${UPSTREAM_STABLE_BRANCH:-main}"
UPSTREAM_BETA_BRANCH="${UPSTREAM_BETA_BRANCH:-cmp-rewrite}"

dry_run=false
do_push=true
channel=""
base_ref_override=""

usage() {
    cat <<'EOF'
Usage: scripts/upstream-sync.sh [options]

Syncs the current channel branch with its upstream base:
  main -> upstream/main          (stable)
  dev  -> upstream/cmp-rewrite   (beta)

Options:
  --channel <stable|beta>  Force a channel instead of inferring it from the branch.
  --base <remote/branch>   Override the upstream ref (e.g. upstream/cmp-rewrite).
  --dry-run                Show what would be merged, without merging or pushing.
  --no-push                Merge locally but do not push to origin.
  --help                   Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) dry_run=true ;;
        --no-push) do_push=false ;;
        --help|-h) usage; exit 0 ;;
        --channel|--base)
            if [[ $# -lt 2 || -z "$2" ]]; then
                echo "Option $1 requires a value." >&2
                usage >&2
                exit 2
            fi
            if [[ "$1" == "--channel" ]]; then
                channel="$2"
            else
                base_ref_override="$2"
            fi
            shift
            ;;
        --channel=*)
            channel="${1#*=}"
            if [[ -z "$channel" ]]; then
                echo "Option --channel requires a value." >&2
                usage >&2
                exit 2
            fi
            ;;
        --base=*)
            base_ref_override="${1#*=}"
            if [[ -z "$base_ref_override" ]]; then
                echo "Option --base requires a value." >&2
                usage >&2
                exit 2
            fi
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
    shift
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
if [[ "$current_branch" == "HEAD" ]]; then
    fail "detached HEAD; switch to $STABLE_BRANCH or $BETA_BRANCH before syncing."
fi

if [[ -z "$channel" ]]; then
    case "$current_branch" in
        "$STABLE_BRANCH") channel=stable ;;
        "$BETA_BRANCH") channel=beta ;;
        *)
            fail "branch '$current_branch' is neither '$STABLE_BRANCH' (stable) nor '$BETA_BRANCH' (beta); pass --channel stable|beta to sync it explicitly."
            ;;
    esac
fi

if [[ "$channel" != "stable" && "$channel" != "beta" ]]; then
    fail "invalid channel '$channel'; expected stable or beta."
fi

if [[ -n "$base_ref_override" ]]; then
    base_ref="$base_ref_override"
    base_label="$base_ref_override"
elif [[ "$channel" == "stable" ]]; then
    base_ref="$BASE_REMOTE/$UPSTREAM_STABLE_BRANCH"
    base_label="$BASE_REMOTE/$UPSTREAM_STABLE_BRANCH"
else
    base_ref="$BASE_REMOTE/$UPSTREAM_BETA_BRANCH"
    base_label="$BASE_REMOTE/$UPSTREAM_BETA_BRANCH"
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
    fail "there are uncommitted tracked changes; commit or stash them before syncing."
fi

if ! git remote get-url "$BASE_REMOTE" >/dev/null 2>&1; then
    echo "Adding remote '$BASE_REMOTE' (https://github.com/NuvioMedia/NuvioMobile.git)"
    git remote add "$BASE_REMOTE" https://github.com/NuvioMedia/NuvioMobile.git
fi

echo "Branch: $current_branch ($channel); upstream base: $base_label"
echo "Fetching $ORIGIN_REMOTE and $BASE_REMOTE..."
git fetch --prune "$ORIGIN_REMOTE"
git fetch --prune "$BASE_REMOTE"

if ! git rev-parse --verify --quiet "$base_ref" >/dev/null; then
    fail "$base_ref does not exist after fetching."
fi

read -r ahead behind < <(git rev-list --left-right --count "HEAD...$base_ref")
echo "Local: $ahead commit(s) ahead, $behind behind $base_ref"

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

remote_branch="$ORIGIN_REMOTE/$current_branch"
if git rev-parse --verify --quiet "$remote_branch" >/dev/null; then
    merge_if_needed "$remote_branch" "chore($current_branch): reconcile $remote_branch"
fi

merge_if_needed "$base_ref" "chore($current_branch): merge $base_ref"

if [[ "$dry_run" == true ]]; then
    echo "[dry-run] no merge or push performed."
    summary "Upstream sync dry-run: $base_ref checked for $current_branch."
    exit 0
fi

if [[ "$do_push" == false ]]; then
    echo "Merge complete; push skipped (--no-push)."
    summary "Upstream sync: local merge complete for $current_branch, push skipped."
    exit 0
fi

echo "Pushing $current_branch to $ORIGIN_REMOTE..."
if ! git push "$ORIGIN_REMOTE" "HEAD:$current_branch"; then
    echo "Push rejected; reconciling $remote_branch and retrying once..."
    git fetch "$ORIGIN_REMOTE"
    merge_if_needed "$remote_branch" "chore($current_branch): reconcile $remote_branch after rejected push"
    git push "$ORIGIN_REMOTE" "HEAD:$current_branch"
fi

echo "Done: $current_branch is up to date with $base_ref."
summary "Upstream sync: $current_branch updated with $base_ref."
