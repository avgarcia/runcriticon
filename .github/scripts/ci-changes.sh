#!/usr/bin/env bash
# Decide qué jobs de ci.yml necesita una PR según los ficheros que cambia (triggers por path, ADR-0010 D16).
#
# Lee los ficheros cambiados por la entrada estándar, uno por línea, y escribe backend/frontend/docker/terraform
# = true|false en $GITHUB_OUTPUT (en la salida estándar si no está definido, para reproducirlo en local:
# `git diff --name-only <merge>^1 <merge> | bash .github/scripts/ci-changes.sh`).
#
# Falla cerrado: un fichero que no encaja en ninguna regla, una lista vacía o un cambio en el propio CI
# activan todos los jobs. Saltarse un job por error deja pasar una rotura a `main`; ejecutar uno de más solo
# cuesta minutos. Los jobs de seguridad no pasan por aquí: corren siempre.
set -euo pipefail

backend=false
frontend=false
docker=false
terraform=false
all=false
seen=false

while IFS= read -r file; do
  [ -z "$file" ] && continue
  seen=true
  case "$file" in
    .github/workflows/ci.yml | .github/scripts/ci-changes.sh)
      # Solo ejecutando todos los jobs se prueba un cambio en el propio CI.
      all=true ;;
    backend/*)
      backend=true; docker=true ;;
    frontend/*)
      frontend=true; docker=true ;;
    api/*)
      # La spec OpenAPI genera los stubs del backend y el cliente del frontend, y entra en la imagen.
      backend=true; frontend=true; docker=true ;;
    schemas/*)
      # Los JSON Schemas de eventos los validan los tests de contrato del backend; no entran en la imagen.
      backend=true ;;
    infrastructure/*)
      # El contrato de variables del staging local (job de la imagen) compara el compose con los .tf de App Runner.
      docker=true
      case "$file" in infrastructure/terraform/*) terraform=true ;; esac ;;
    .dockerignore)
      docker=true ;;
    .editorconfig)
      # Lo leen ktlint y Prettier.
      backend=true; frontend=true ;;
    docs/* | .claude/* | .github/* | *.md | package.json | package-lock.json | .log4brains.yml \
      | docker-compose.yml | .gitleaksignore | .gitignore)
      # Sin efecto en ningún job con filtro: el sitio de ADRs tiene su propio workflow, el compose de la raíz
      # es solo de desarrollo local y gitleaks corre siempre.
      ;;
    *)
      all=true ;;
  esac
done

if [ "$seen" = false ] || [ "$all" = true ]; then
  backend=true
  frontend=true
  docker=true
  terraform=true
fi

{
  echo "backend=$backend"
  echo "frontend=$frontend"
  echo "docker=$docker"
  echo "terraform=$terraform"
} >> "${GITHUB_OUTPUT:-/dev/stdout}"
