# Onboarding — arranque en local

Guía para que un dev nuevo tenga backend + frontend corriendo en local y entre en la aplicación en **menos de 30 minutos**. Solo cubre el arranque: la arquitectura y las decisiones viven en los documentos enlazados en [Siguientes lecturas](#siguientes-lecturas).

## 1. Prerrequisitos

| Herramienta | Versión | Notas |
|---|---|---|
| **Git** | cualquiera reciente | — |
| **JDK para lanzar Gradle** | 17 o superior en el `PATH` / `JAVA_HOME` | Gradle 9 necesita una JVM 17+ solo para **arrancar** el wrapper. El JDK con el que se **compila** (GraalVM CE 21, ADR-0016 D7) lo descarga Gradle solo: `toolchain { languageVersion = 21 }` en `backend/build.gradle.kts` + plugin `foojay-resolver-convention` en `backend/settings.gradle.kts`. |
| **Docker** (Docker Desktop en Windows/macOS) | con `docker compose` | Postgres + MailHog en local y Testcontainers en los tests de integración. |
| **Node.js** | la de [`frontend/.nvmrc`](../frontend/.nvmrc) (hoy `22.22.3`) | Con `nvm use` (o `nvm-windows`) desde `frontend/`. CI usa esa misma versión (`node-version-file: frontend/.nvmrc` en `.github/workflows/ci.yml`) con la npm que trae incorporada. |

No hacen falta credenciales de AWS ni de Postmark para desarrollar en local (ver §5 y §8).

## 2. Clonar y levantar la infraestructura local

```bash
git clone https://github.com/avgarcia/runcriticon.git
cd runcriticon

docker-compose up -d        # Postgres 16 + MailHog
docker-compose ps           # esperar a que postgres salga "healthy"
```

Lo que levanta [`docker-compose.yml`](../docker-compose.yml):

| Servicio | Puerto | Credenciales / URL |
|---|---|---|
| Postgres 16 (`runcriticon-postgres`) | `5432` | BD `runcriticon_local`, usuario `runcriticon`, contraseña `local-dev-not-prod` (valores fake, ADR-0013 D13) |
| MailHog (`runcriticon-mailhog`) | `1025` (SMTP), `8025` (UI) | http://localhost:8025 |

> **Aviso**: hoy el perfil `local` **no envía emails a MailHog**. El adaptador activo es `StubEmailSender` (`@Profile("local")`), que escribe el enlace en el log del backend. Ver §5.

## 3. Arrancar el backend

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

- **Sin el perfil `local` no arranca**: el datasource solo está definido en [`application-local.yml`](../backend/src/main/resources/application-local.yml). Equivalente: `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`.
- La primera ejecución tarda más: Gradle descarga la toolchain GraalVM 21 y las dependencias.
- Flyway aplica las migraciones de todos los módulos al arrancar.
- Escucha en `:8080`. **Primer éxito del backend**: http://localhost:8080/actuator/health devuelve `UP` (endpoint público, sin sesión).

## 4. Arrancar el frontend

```bash
cd frontend
nvm use                 # lee .nvmrc (con nvm-windows: nvm use 22.22.3)
npm ci                  # o npm install; ver "Problemas conocidos" antes de tocar el lock
npm run gen:api         # genera el cliente HTTP desde ../api/openapi.yaml
npm start               # ng serve en http://localhost:4200
```

- **`npm run gen:api` es obligatorio** antes del primer `npm start`/`npm test`: el cliente generado (`frontend/src/app/api/generated/`) está en `.gitignore` y se regenera en cada build (ADR-0001 D10). Repítelo cuando cambie `api/openapi.yaml`.
- [`frontend/proxy.conf.json`](../frontend/proxy.conf.json) proxya `/api` y `/actuator` a `http://localhost:8080`, simulando el mismo origen de producción (ADR-0001 D11): sin CORS, cookie de sesión first-party.

## 5. Entrar en la aplicación por primera vez

El perfil `local` siembra un admin al arrancar (`IdentidadSeeder`, `@Profile("local", "staging")`, ADR-0003 D3) con los valores de `runcriticon.bootstrap.*` de `application-local.yml`:

| Campo | Valor local |
|---|---|
| Email | `admin@runcriticon.local` |
| Contraseña | `cambia-esta-password-local` |
| Club | `00000000-0000-0000-0000-000000000001` |

1. Abre http://localhost:4200/login.
2. Entra con el email y la contraseña de la tabla. **Primer éxito completo**: ves la pantalla de gestión del admin.

**Enlaces de email (invitaciones, magic link, reseteo)**: en `local` no hay email real. `StubEmailSender` escribe en la consola del backend una línea `[STUB-EMAIL] … token=… expira=…`. Construye la URL a mano con ese token:

| Flujo | URL |
|---|---|
| Activación de una invitación | `http://localhost:4200/activar?token=<token>` |
| Magic link | `http://localhost:4200/entrar?token=<token>` |
| Reseteo de contraseña | `http://localhost:4200/restablecer/nueva?token=<token>` |

> El seeder es idempotente y **no re-hashea** un admin que ya existe. Si cambias `admin-password` en el yml después del primer arranque, no surte efecto hasta que borres esa fila (o el volumen: `docker-compose down -v`, que **borra toda la BD local**).

## 6. Tests y calidad

Backend (desde `backend/`):

```bash
./gradlew build                          # compila + tests + ArchUnit + Modulith (requiere Docker)
./gradlew test --tests "*CapasArchTest"   # un test o patrón concreto
./gradlew detekt ktlintCheck             # estilo estático
./gradlew ktlintFormat                   # autoformato (p. ej. orden de imports)
./gradlew contractTest                   # tests de contrato JSON Schema
```

- Los tests de integración usan **Testcontainers**: la tarea `checkDockerAvailable` falla rápido si `docker info` no responde.
- **Sin Docker** puedes verificar compilación + reglas de arquitectura:

  ```bash
  ./gradlew test --tests "*ArchTest" -x checkDockerAvailable
  ```

Frontend (desde `frontend/`):

```bash
npm test          # Jest
npm run e2e       # Playwright + axe-core
npm run lint      # ng lint (ESLint)
npm run format:check
```

## 7. Problemas conocidos en Windows

| Síntoma | Causa | Solución |
|---|---|---|
| Testcontainers falla con `permission denied` al abrir el pipe de Docker | El usuario de Windows con el que corre el build no está en el grupo local `docker-users`, o el contexto de Docker no es el correcto | Añadir el usuario a `docker-users` (requiere cerrar sesión) y fijar el contexto: `docker context use default` (no `desktop-windows`) |
| `InvalidPathException: Illegal char <">` en Testcontainers | Alguna entrada del `PATH` de la máquina lleva comillas | Sanear la variable `PATH` del sistema/usuario (quitar las comillas) y abrir una terminal nueva. No es un fallo del código |
| El backend no arranca en `local` con «Credenciales AWS reales detectadas en perfil local» | Hay `AWS_ACCESS_KEY_ID` / `AWS_SESSION_TOKEN` reales en el entorno; `LocalProfileGuard` bloquea el arranque (ADR-0013 D13) | Arrancar desde una terminal sin esas variables (`unset AWS_ACCESS_KEY_ID AWS_SESSION_TOKEN`) |
| `npm ci` falla en CI tras regenerar `frontend/package-lock.json` en local | El lock se regeneró con una npm distinta de la que usa CI (la incluida en el Node de `.nvmrc`) | Regenerar con la npm de esa línea: `npx npm@10.9.4 install` desde `frontend/`, no con la npm global |
| Gradle se queda colgado sin avanzar | Lock de un daemon de Gradle previo | `./gradlew --stop` y repetir |
| Las PRs de Dependabot del backend fallan en CI | El repo usa *dependency locking* (`dependencyLocking { lockAllConfigurations() }`, `backend/gradle.lockfile`); Dependabot no regenera el lock (ADR-0010) | Regenerar en un commit aparte: `./gradlew dependencies --write-locks`. Hacer rebase no lo arregla |

## 8. Secretos

En local **no se usa ningún secreto real**: todo son valores fake con prefijo `local-dev-…-not-prod` en `application-local.yml`, y el perfil local tiene prohibido leer SSM (ADR-0013 D13). Si alguna vez necesitas consultar un secreto de un entorno remoto, sigue el runbook [`runbooks/acceso-secretos.md`](runbooks/acceso-secretos.md).

## Siguientes lecturas

- [`CLAUDE.md`](../CLAUDE.md) — reglas globales del monorepo, stack y convenciones críticas.
- [`docs/arquitectura/estructura-de-un-modulo.md`](arquitectura/estructura-de-un-modulo.md) — cómo se construye un módulo, con sus 5 subdocumentos.
- [`docs/adr/README.md`](adr/README.md) — índice de los 17 ADRs aceptados (fuente de verdad).
- [`docs/glosario.md`](glosario.md) — lenguaje ubicuo del negocio.
- [`docs/formacion/README.md`](formacion/README.md) — planes de formación del equipo.
