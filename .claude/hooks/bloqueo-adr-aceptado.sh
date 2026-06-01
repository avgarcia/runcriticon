#!/usr/bin/env bash
# Hook PreToolUse — Bloquea edición de ADRs en estado Aceptado.
#
# Un ADR aceptado no se reescribe sobre la marcha (regla del corpus). Cualquier
# cambio pasa por una PR de revisión en rama feature/revision-adr-NNNN.
#
# Política:
#   - Si el archivo es docs/adr/NNNN-*.md y contiene "Estado**: Aceptado",
#     bloquea (exit 2) SALVO que la rama actual sea feature/revision-adr-NNNN
#     o feature/acepta-adr-NNNN.
#   - Permite siempre docs/adr/README.md, index.md, template.md.
#
# Cruce: docs/adr/README.md §"Cómo escribir un ADR" (un ADR aceptado no se
# borra ni se reescribe), .claude/skills/adr-review/SKILL.md.

set -uo pipefail

paths="${CLAUDE_FILE_PATHS:-${CLAUDE_TOOL_RESPONSE_FILE_PATH:-}}"
[ -z "$paths" ] && exit 0

rama="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '')"

while IFS= read -r path; do
    [ -z "$path" ] && continue

    # Solo ADRs numerados (no README/index/template)
    if echo "$path" | grep -qE 'docs/adr/[0-9]{4}-.+\.md$'; then
        # ¿Está aceptado?
        if [ -f "$path" ] && grep -qE '^\s*-\s*\*\*Estado\*\*:\s*Aceptado' "$path"; then
            # Extraer NNNN del nombre
            nnnn="$(basename "$path" | grep -oE '^[0-9]{4}')"
            # Permitir si la rama es de revisión/aceptación de ESE ADR
            if echo "$rama" | grep -qE "(revision|acepta)-adr-${nnnn}"; then
                continue
            fi
            echo "🛑 BLOQUEADO: edición a $path (ADR Aceptado)" >&2
            echo "" >&2
            echo "   Un ADR Aceptado no se reescribe sobre la marcha." >&2
            echo "   Abre una rama feature/revision-adr-${nnnn} y usa la skill /adr-review." >&2
            echo "   Rama actual: ${rama:-desconocida}" >&2
            exit 2
        fi
    fi
done <<< "$paths"

exit 0
