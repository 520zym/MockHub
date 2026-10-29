#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
node build/workspace.mjs
node build/teams.mjs
node build/account.mjs
node build/access.mjs
node build/policy.mjs
node build/gaps.mjs
node ../build/fit-board.mjs
node build/render.mjs --all
node build/gallery.mjs
node build/render.mjs --boards
node build/render.mjs --gallery
node build/verify.mjs
printf "Complete: permission scenes, review boards, local viewer.\n"
