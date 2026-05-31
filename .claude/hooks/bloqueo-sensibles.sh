#!/usr/bin/env bash
# Hook PreToolUse — Bloquea ediciones a archivos sensibles del proyecto.
#
# Invocado por Claude Code antes de Edit/Write. Recibe el path en $CLAUDE_FILE_PATHS.
# Si detecta un path sensible, devuelve exit 2 (bloqueo).
#
# Política:
#   - Bloquea ediciones a .tfvars (variables Terraform con valores reales).
#   - Bloquea ediciones a secrets.yaml / secrets.yml (secretos hardcoded).
#   - Permite excepciones explícitas: terraform.tfvars.example, *.example.*
#
# Cruce:
#   - ADR-0013 D12: ningún secreto en repo, escaneo CI lo vigila.
#   - .gitignore raíz ya ignora *.tfvars salvo example.tfvars y terraform.tfvars.example.
#   - Este hook añade defensa adicional para que Claude no genere accidentalmente
#     un .tfvars con valores reales.

set -euo pipefail

# Path candidato a editar
paths="${CLAUDE_FILE_PATHS:-}"

if [ -z "$paths" ]; then
    exit 0
fi

# Detectar archivos sensibles, excluyendo los .example
while IFS= read -r path; do
    if [ -z "$path" ]; then
        continue
    fi

    # Permitir explícitamente templates de ejemplo
    if echo "$path" | grep -qE '(example\.tfvars$|\.example\.|\.example$)'; then
        continue
    fi

    # Bloquear .tfvars reales
    if echo "$path" | grep -qE '\.tfvars$'; then
        echo "🛑 BLOQUEADO: edición a $path" >&2
        echo "" >&2
        echo "   .tfvars contiene valores reales y NO se commitea (cruce .gitignore + ADR-0013 D12)." >&2
        echo "   Si necesitas el archivo localmente, créalo a mano fuera de Claude." >&2
        echo "   Si quieres editar la plantilla, usa terraform.tfvars.example." >&2
        exit 2
    fi

    # Bloquear secrets.yaml / secrets.yml
    if echo "$path" | grep -qE '(/|^)secrets\.ya?ml$'; then
        echo "🛑 BLOQUEADO: edición a $path" >&2
        echo "" >&2
        echo "   Los secretos viven en SSM Parameter Store (ADR-0013 D3-D5)." >&2
        echo "   Ningún archivo del repo debe contener secretos en claro." >&2
        echo "   Si necesitas configurar SSM, usa el runbook docs/runbooks/rotacion-{secreto}.md" >&2
        exit 2
    fi

done <<< "$paths"

exit 0
