#!/usr/bin/env bash
# Hook PostToolUse — Escanea el archivo recién editado en busca de secretos.
#
# Cruce: ADR-0010 D7 (quality gate: escaneo de secretos con gitleaks),
# ADR-0013 D12 (ningún secreto en el repo).
#
# Política: si gitleaks detecta un secreto, exit 2 (Claude recibe el aviso y debe
# deshacer/corregir). El gate definitivo es el CI; este hook lo adelanta a local.
#
# Best-effort: si gitleaks no está instalado, salta silenciosamente.

set -uo pipefail

command -v gitleaks >/dev/null 2>&1 || exit 0

paths="${CLAUDE_FILE_PATHS:-${CLAUDE_TOOL_RESPONSE_FILE_PATH:-}}"
[ -z "$paths" ] && exit 0

hubo_secreto=0
while IFS= read -r path; do
    [ -z "$path" ] && continue
    [ -f "$path" ] || continue

    # Ignorar archivos de ejemplo/test con valores fake conocidos
    if echo "$path" | grep -qE '(\.example|application-(local|test)\.ya?ml|test-only-not-for-prod)'; then
        continue
    fi

    if ! gitleaks detect --no-git --source "$path" --redact --exit-code 1 >/tmp/gitleaks-hook.out 2>&1; then
        echo "🛑 POSIBLE SECRETO detectado por gitleaks en $path" >&2
        echo "" >&2
        tail -20 /tmp/gitleaks-hook.out >&2
        echo "" >&2
        echo "   Los secretos viven en SSM Parameter Store (ADR-0013 D3-D5), nunca en el repo." >&2
        echo "   Deshaz el cambio y usa una env var o un valor fake con prefijo test-only-not-for-prod." >&2
        hubo_secreto=1
    fi
done <<< "$paths"

[ "$hubo_secreto" -eq 1 ] && exit 2
exit 0
