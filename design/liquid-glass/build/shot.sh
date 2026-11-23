#!/usr/bin/env bash
# Generate the complete approved flat suite and audit real browser rendering.
set -euo pipefail
cd "$(dirname "$0")/.."
node build/link-pages.mjs
node build/flows-pages.mjs
node build/workspace-pages.mjs
node build/admin-pages.mjs
node build/settings-pages.mjs
node build/match-rules.mjs
node build/fit-board.mjs
node build/render.mjs --all
node build/render.mjs 22-match-rules --test-rules
node build/gallery.mjs
node build/render.mjs --boards
node build/render.mjs --gallery
node build/verify-preview.mjs
printf "Complete: 22 page screenshots, 4 contact sheets, gallery and viewer.\n"
