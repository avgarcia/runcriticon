#!/usr/bin/env bash
# Hook SessionStart — Carga contexto del módulo activo al iniciar la sesión.
#
# Detecta en qué módulo del backend hay cambios sin commitear (o en la rama vs main)
# y sugiere a Claude qué documentos de arquitectura precargar. La salida del hook se
# añade al contexto inicial de la sesión.
#
# Cruce: docs/arquitectura/estructura-de-un-modulo.md + 5 subdocumentos.
#
# Best-effort: si no hay repo git o no hay cambios, no dice nada.

set -uo pipefail

command -v git >/dev/null 2>&1 || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

# Archivos cambiados en working tree + diff contra main
cambios="$(
    {
        git diff --name-only 2>/dev/null
        git diff --name-only --cached 2>/dev/null
        git diff --name-only main...HEAD 2>/dev/null
    } | sort -u
)"

[ -z "$cambios" ] && exit 0

# ¿Qué módulos del backend están tocados?
modulos="$(echo "$cambios" | grep -oE 'backend/src/[^/]+/kotlin/com/runcriticon/(identidad|club|planificacion|salud|auditoria)' | grep -oE '(identidad|club|planificacion|salud|auditoria)' | sort -u || true)"

[ -z "$modulos" ] && exit 0

echo "📦 Contexto de módulo detectado para esta sesión:"
echo ""
echo "   Módulos del backend con cambios: $(echo "$modulos" | tr '\n' ' ')"
echo ""
echo "   Documentos de arquitectura relevantes (espejo aplicado de los ADRs):"
echo "   - docs/arquitectura/estructura-de-un-modulo.md (guía principal)"

# Sugerir subdocumentos según qué tipos de archivo se han tocado
if echo "$cambios" | grep -qE '(migration/.*\.sql|persistencia|Entity\.kt|Mapper\.kt)'; then
    echo "   - docs/arquitectura/persistencia.md (migraciones / Konvert / JSONB)"
fi
if echo "$cambios" | grep -qE '(Test\.kt|test/)'; then
    echo "   - docs/arquitectura/testing-de-modulos.md (ArchUnit / acceso cruzado / Testcontainers)"
fi
if echo "$cambios" | grep -qE '(BorradoAlumno|RgpdCategory|consentimiento|auditoria)'; then
    echo "   - docs/arquitectura/rgpd-en-modulos.md (borrado mixto / @RgpdCategory)"
fi
if echo "$cambios" | grep -qE '(Metricas|observabilidad|MDC|trace)'; then
    echo "   - docs/arquitectura/observabilidad-por-modulo.md (métricas / MDC / traceparent)"
fi
if echo "$cambios" | grep -qE '(Properties|config|secret|ssm)'; then
    echo "   - docs/arquitectura/configuracion-y-secretos-en-modulos.md (ConfigurationProperties / SSM)"
fi

echo ""
echo "   Recordatorio: el código es espejo aplicado de los ADRs. Si dudas, gana el ADR."

exit 0
