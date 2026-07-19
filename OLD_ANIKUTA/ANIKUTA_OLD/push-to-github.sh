#!/bin/bash
# Push both branches to GitHub to trigger CI builds.
#
# Usage:
#   ./push-to-github.sh                     # pushes both branches
#   ./push-to-github.sh feature/dev         # pushes only feature/dev
#   ./push-to-github.sh main                # pushes only main
#
# If you get "could not read Username" errors, you need to set up auth:
#   Option A: Use a Personal Access Token (PAT)
#     1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
#     2. Generate a token with "repo" scope
#     3. Run: git remote set-url origin https://<USERNAME>:<TOKEN>@github.com/testplay-byte/anikuta.git
#
#   Option B: Use GitHub CLI
#     1. Install: sudo apt install gh
#     2. Authenticate: gh auth login
#     3. Re-run this script

set -e

cd "$(dirname "$0")"

BRANCH="${1:-both}"

push_branch() {
    local branch=$1
    echo "============================================"
    echo "Pushing $branch to origin..."
    echo "============================================"
    git push origin "$branch"
    echo ""
    echo "✅ Pushed $branch successfully!"
    echo "   CI build will trigger at:"
    echo "   https://github.com/testplay-byte/anikuta/actions"
    echo ""
}

case "$BRANCH" in
    both)
        push_branch "feature/dev"
        push_branch "main"
        ;;
    feature/dev|main)
        push_branch "$BRANCH"
        ;;
    *)
        echo "Unknown branch: $BRANCH"
        echo "Usage: $0 [feature/dev|main|both]"
        exit 1
        ;;
esac

echo "============================================"
echo "Done! Check CI at:"
echo "  https://github.com/testplay-byte/anikuta/actions"
echo ""
echo "Download the APK from the build artifacts once CI passes."
echo "============================================"
