#!/usr/bin/env bash
# Smoke del staging local (H0a): la app arranca detrás del proxy TLS, se puede iniciar sesión con el admin de
# arranque y se ve una pantalla. Mismo flujo que LoginSmokeTest, pero contra la imagen de producción desplegada como
# en App Runner. Requiere el entorno levantado (`docker compose up -d`) y curl.
#
# Los atributos de las cookies se leen de las cabeceras Set-Cookie, no del cookie jar de curl: en el jar las cookies
# HttpOnly llevan el prefijo `#HttpOnly_` y sus atributos no se ven.
set -euo pipefail

BASE_URL="${BASE_URL:-https://localhost:8443}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@runcriticon.staging}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-staging-local}"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-300}"

COMPOSE=(docker compose -f "$(dirname "$0")/docker-compose.yml")
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fail() {
  echo "SMOKE KO: $*" >&2
  echo "--- últimas líneas de la app ---" >&2
  "${COMPOSE[@]}" logs --no-color --tail=150 app >&2 || true
  exit 1
}

# Cabecera Set-Cookie de [nombre] en el volcado de cabeceras [fichero], sin el \r final.
set_cookie() {
  grep -i "^set-cookie: $2=" "$1" | tail -1 | tr -d '\r' || true
}

expect_attribute() {
  local cookie="$1" attribute="$2" why="$3"
  grep -qi "; *$attribute" <<<"$cookie" || fail "$why (cabecera: $cookie)"
}

# 1. Arranque: /actuator/health UP a través del proxy. Si el contenedor termina (configuración que falta, Flyway que
#    falla), no se espera al timeout.
deadline=$((SECONDS + TIMEOUT_SECONDS))
until curl -ksf "$BASE_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
  if "${COMPOSE[@]}" ps --services --status exited | grep -qx app; then
    fail "el contenedor de la app terminó durante el arranque"
  fi
  ((SECONDS < deadline)) || fail "la app no respondió UP en ${TIMEOUT_SECONDS}s"
  sleep 3
done
echo "ok  /actuator/health UP"

# 2. Handshake CSRF: sin sesión, 401 y cookie XSRF-TOKEN. Su `Secure` lo decide request.isSecure(), así que es la
#    prueba de que la app ve la petición como HTTPS a través del proxy (X-Forwarded-Proto).
code=$(curl -ks -o /dev/null -D "$WORK/handshake" -c "$WORK/jar" -w '%{http_code}' "$BASE_URL/api/sesion/actual")
[[ "$code" == 401 ]] || fail "GET /api/sesion/actual sin sesión devolvió $code, se esperaba 401"
xsrf=$(set_cookie "$WORK/handshake" XSRF-TOKEN)
[[ -n "$xsrf" ]] || fail "el handshake no emitió la cookie XSRF-TOKEN"
expect_attribute "$xsrf" Secure "XSRF-TOKEN sin Secure: la app no ve HTTPS detrás del proxy (X-Forwarded-Proto)"
token=$(sed -E 's/^[^:]+: *XSRF-TOKEN=([^;]*).*/\1/' <<<"$xsrf")
echo "ok  handshake CSRF con XSRF-TOKEN Secure"

# 3. Login del admin de arranque: prueba de paso que el seeder recibió RUNCRITICON_BOOTSTRAP_* y que Flyway migró.
code=$(curl -ks -o "$WORK/login" -D "$WORK/login-headers" -b "$WORK/jar" -c "$WORK/jar" -w '%{http_code}' \
  -H "X-XSRF-TOKEN: $token" -H 'Content-Type: application/json' \
  --data "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  "$BASE_URL/api/sesion")
[[ "$code" == 200 ]] || fail "el login devolvió $code: $(head -c 300 "$WORK/login")"
grep -q ADMIN "$WORK/login" || fail "el login no devolvió el principal ADMIN: $(head -c 300 "$WORK/login")"
session=$(set_cookie "$WORK/login-headers" SESSION)
[[ -n "$session" ]] || fail "el login no emitió la cookie SESSION"
expect_attribute "$session" Secure "la cookie de sesión no lleva Secure"
expect_attribute "$session" HttpOnly "la cookie de sesión no lleva HttpOnly"
expect_attribute "$session" "SameSite=Lax" "la cookie de sesión no lleva SameSite=Lax"
echo "ok  login del admin con cookie de sesión Secure, HttpOnly y SameSite=Lax"

# 4. La sesión se mantiene en la siguiente petición.
code=$(curl -ks -o /dev/null -b "$WORK/jar" -w '%{http_code}' "$BASE_URL/api/sesion/actual")
[[ "$code" == 200 ]] || fail "GET /api/sesion/actual con sesión devolvió $code"
echo "ok  sesión activa"

# 5. Una pantalla: la SPA servida por la propia app, en el mismo origen, y sin sesión — si no, nadie llegaría a la
#    pantalla de login ni al enlace de activación de su email. La API sigue cerrada.
for path in / /mi-plan "/activacion?token=x"; do
  code=$(curl -ks -o "$WORK/index" -w '%{http_code}' "$BASE_URL$path")
  [[ "$code" == 200 ]] || fail "GET $path sin sesión devolvió $code"
  grep -q '<rc-root' "$WORK/index" || fail "GET $path no sirvió la SPA (sin <rc-root>)"
done
# La CSP de la app (script-src 'self') bloquea cualquier script o manejador inline del index.html: si el build los
# genera, curl ve la página bien pero el navegador no la pinta entera.
inline=$(grep -oiE '<script(\s[^>]*)?>' "$WORK/index" | grep -viE '\ssrc=' || true)
[[ -z "$inline" ]] || fail "el index.html lleva un <script> inline que la CSP bloquea: $inline"
handlers=$(grep -oiE '<[a-z][^>]*\son[a-z]+=' "$WORK/index" || true)
[[ -z "$handlers" ]] || fail "el index.html lleva manejadores inline que la CSP bloquea: $handlers"
code=$(curl -ks -o /dev/null -w '%{http_code}' "$BASE_URL/api/no-existe")
[[ "$code" == 401 ]] || fail "GET /api/no-existe sin sesión devolvió $code, se esperaba 401"
code=$(curl -ks -o /dev/null -w '%{http_code}' "$BASE_URL/chunk-inexistente.js")
[[ "$code" == 404 ]] || fail "un bundle inexistente devolvió $code, se esperaba 404"
echo "ok  la SPA se sirve sin sesión en / y en los enlaces profundos, sin nada inline que bloquee la CSP; la API sigue cerrada"

echo "SMOKE OK"
