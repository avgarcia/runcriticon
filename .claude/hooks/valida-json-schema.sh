#!/usr/bin/env bash
# Hook PostToolUse — Valida que un JSON Schema de integration event sea correcto.
#
# Tras editar/crear schemas/{modulo}/{evento}-v{N}.json, comprueba:
#   1. JSON válido (parseable).
#   2. Declara $schema 2020-12.
#   3. Contiene los 6 campos obligatorios de IntegrationEvent + traceparent opcional.
#
# Cruce: ADR-0007 D11 (versionado de eventos con JSON Schema), schemas/README.md.
#
# Best-effort: usa python3 si está; si no, salta silenciosamente.

set -uo pipefail

paths="${CLAUDE_FILE_PATHS:-${CLAUDE_TOOL_RESPONSE_FILE_PATH:-}}"

schema_files=$(echo "$paths" | grep -E 'schemas/[^/]+/.+\.json$' || true)
[ -z "$schema_files" ] && exit 0

command -v python3 >/dev/null 2>&1 || exit 0

problemas=0
for f in $schema_files; do
    [ -f "$f" ] || continue

    resultado="$(python3 - "$f" <<'PY'
import json, sys
ruta = sys.argv[1]
obligatorios = {"eventId", "aggregateId", "occurredAt", "version", "clubId", "actorId"}
try:
    with open(ruta, encoding="utf-8") as fh:
        doc = json.load(fh)
except Exception as e:
    print(f"JSON inválido: {e}")
    sys.exit(0)

avisos = []
sch = doc.get("$schema", "")
if "2020-12" not in sch:
    avisos.append("falta $schema 2020-12")

props = set((doc.get("properties") or {}).keys())
faltan = obligatorios - props
if faltan:
    avisos.append(f"faltan campos obligatorios de IntegrationEvent: {sorted(faltan)}")
if "traceparent" not in props:
    avisos.append("falta el campo opcional traceparent (ADR-0011 D4)")

if avisos:
    print(" · ".join(avisos))
PY
)"

    if [ -n "$resultado" ]; then
        echo "⚠️  Schema $f: $resultado" >&2
        problemas=1
    fi
done

# Informativo, no bloqueante (el gate real es el job contractTest del CI).
exit 0
