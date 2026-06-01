#!/usr/bin/env bash
# Hook PostToolUse — Aviso si una migración SQL se ha tocado sin actualizar el RAT.
#
# Invocado por Claude Code tras Edit/Write. Recibe el path en $CLAUDE_FILE_PATHS
# (uno o varios separados por nueva línea según versión del runtime).
#
# Política: NO bloquea (no es un error técnico). Solo recuerda.
# Cruce: ADR-0014 D19 — "Un pre-commit hook señala las PRs que tocan migraciones
# SQL sin tocar el RAT (no es bloqueante: hay cambios técnicos que no afectan al
# RAT; el hook fuerza a justificarlo en la PR)".

set -euo pipefail

# Filtrar paths que correspondan a migraciones Flyway de un módulo
migration_paths=$(echo "${CLAUDE_FILE_PATHS:-}" | grep -E 'backend/src/main/resources/db/migration/[^/]+/V[0-9]{12}__.+\.sql$' || true)

if [ -n "$migration_paths" ]; then
    echo ""
    echo "⚠️  Migración SQL tocada:"
    echo "$migration_paths" | sed 's/^/    /'
    echo ""
    echo "   ¿Has actualizado docs/legal/rat.md según ADR-0014 D19?"
    echo "   El RAT debe reflejar tablas nuevas, cambios de categoría RGPD,"
    echo "   modificaciones de retención o de propósito de tratamiento."
    echo ""
fi

# Exit 0 siempre (informativo, nunca bloqueante).
exit 0
