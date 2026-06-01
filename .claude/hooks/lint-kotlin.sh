#!/usr/bin/env bash
# Hook PostToolUse — ktlint (format) + detekt sobre archivos Kotlin editados.
#
# Recomendación: ktlint corre rápido y autoformatea; detekt es más pesado.
# Cruce: ADR-0010 D7 (quality gates), backend/CLAUDE.md (detekt + ktlint en cada build).
#
# Best-effort: si el proyecto Gradle aún no existe (H0 pre-Bloque 2A), no hace nada.

set -uo pipefail

paths="${CLAUDE_FILE_PATHS:-${CLAUDE_TOOL_RESPONSE_FILE_PATH:-}}"

# ¿Algún .kt o .kts entre los paths tocados?
kotlin_files=$(echo "$paths" | grep -E '\.(kt|kts)$' || true)
[ -z "$kotlin_files" ] && exit 0

# ¿Existe el wrapper de Gradle? (no en H0 pre-Bloque 2A)
if [ ! -f "backend/gradlew" ] && [ ! -f "gradlew" ]; then
    exit 0
fi

GRADLEW="./gradlew"
[ -f "backend/gradlew" ] && GRADLEW="backend/gradlew"
GRADLE_DIR="."
[ -f "backend/gradlew" ] && GRADLE_DIR="backend"

# ktlint con autoformato (rápido). No bloquea: solo reporta el resultado.
( cd "$GRADLE_DIR" && ./gradlew ktlintFormat --quiet 2>&1 | tail -15 ) || true

# detekt (análisis estático). Reporta hallazgos sin bloquear el flujo de edición;
# el gate real es el CI (ADR-0010 D7).
( cd "$GRADLE_DIR" && ./gradlew detekt --quiet 2>&1 | tail -20 ) || true

exit 0
