# E2E contra el backend real

El entorno del recorrido [`frontend/e2e-real`](../../frontend/e2e-real): la imagen de producción con la SPA y la API en el mismo origen, más Postgres, y nada mockeado. Complementa a los e2e de `frontend/e2e`, que interceptan toda la red con `page.route()` y por eso no ven si el frontend y el backend encajan: el contrato, los eventos del outbox, las proyecciones y la cadena de seguridad real.

## Qué recorre

El bucle central del producto: el entrenador crea el plan de la semana, le añade la sesión de hoy y lo publica a su grupo; el alumno activa su cuenta con su invitación (con el consentimiento de datos de salud) y ve la sesión en su semana.

Los datos de partida (entrenador, grupo y alumno invitado) se crean por API con el admin de arranque. Todo lo demás pasa por la interfaz.

## Por qué el perfil `local` y no `staging`

El alta es solo por invitación y el token solo viaja por email. Con el perfil `local`, `StubEmailSender` lo escribe en el log de la app en lugar de enviarlo a Postmark, y el test lo lee de ahí. El perfil `staging` (el del [staging local](../staging-local)) usa Postmark y no deja ver los tokens. Es la única diferencia entre los dos entornos; ninguno de los dos toca el código de producción.

## Uso en local

Desde la raíz del repo (necesita Docker; los comandos de Playwright, en `frontend/`):

```bash
docker compose -f infrastructure/e2e/docker-compose.yml up -d --build
```

```bash
cd frontend && npm ci && npx playwright install chromium && npm run e2e:real
```

```bash
docker compose -f infrastructure/e2e/docker-compose.yml down -v
```

La app queda en <http://localhost:8080>. `--build` construye la imagen entera dentro de Docker y tarda varios minutos la primera vez.

## Límites de tasa

El backend limita por IP, entre otras cosas, la consulta de la invitación en la pantalla de activación (429 al agotar el cupo), y ese cupo no se reinicia entre ejecuciones. Un recorrido por entorno nunca lo agota; muchas ejecuciones seguidas contra el mismo, sí, y el test falla en la consulta de la invitación con el 429. Si pasa, recrea el entorno (`down -v` y `up`).

## En CI

El job `e2e-real` de `.github/workflows/ci.yml` construye la imagen (con la caché de layers del job de la imagen), levanta este entorno y ejecuta el recorrido. Corre cuando la imagen corre: si la PR toca backend, frontend, `api/` o `infrastructure/`. Si falla, sube las trazas de Playwright y el log de la app.
