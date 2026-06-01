#!/usr/bin/env bash
# Hook PostToolUse — ESLint + Prettier sobre archivos del frontend editados.
#
# Cruce: ADR-0012 D11 (ESLint + Prettier), frontend/CLAUDE.md.
#
# Best-effort: si el proyecto Angular aún no existe (H0 pre-Bloque 2A) o no hay
# node_modules, no hace nada.

set -uo pipefail

paths="${CLAUDE_FILE_PATHS:-${CLAUDE_TOOL_RESPONSE_FILE_PATH:-}}"

# ¿Algún archivo de frontend entre los paths tocados?
frontend_files=$(echo "$paths" | grep -E 'frontend/.*\.(ts|html|scss|css)$' || true)
[ -z "$frontend_files" ] && exit 0

# ¿Existe el proyecto Angular con node_modules?
if [ ! -f "frontend/package.json" ] || [ ! -d "frontend/node_modules" ]; then
    exit 0
fi

# Prettier (autoformato) + ESLint (fix) solo sobre los archivos tocados.
# No bloquea: el gate real es el CI.
for f in $frontend_files; do
    rel="${f#frontend/}"
    ( cd frontend && npx prettier --write "$rel" 2>&1 | tail -3 ) || true
    ( cd frontend && npx eslint --fix "$rel" 2>&1 | tail -10 ) || true
done

exit 0
