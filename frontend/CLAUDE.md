# CLAUDE.md — Frontend (Angular)
Reglas específicas del frontend. Las reglas globales (arquitectura de módulos, lenguaje ubicuo, contrato OpenAPI, reglas de dominio) están en [`../CLAUDE.md`](../CLAUDE.md).

## Estado
**Hito H0 en curso** — el proyecto Angular 19 ya está montado: `login`, `home`, `core/auth.guard`, `core/sesion.service` (con specs), con Jest y Playwright configurados. Las maquetas hi-fi en `../docs/diseno/` siguen siendo la referencia visual al construir nuevas pantallas.

## Stack
- **Angular** con **componentes standalone** y TypeScript.
- **Carga diferida por ruta** — ningún módulo eager más allá del shell.
- **Material** como librería de componentes base (ADR-0012).
- Estilo: **ESLint** + **Prettier**.
- Build/dev: **Angular CLI**.

## Comandos
El proyecto Angular ya existe. Scripts (`package.json`):

```bash
npm install
npm run start    # ng serve
npm run build    # ng build (producción)
npm run test     # jest (unit)
npm run lint     # eslint
npm run e2e      # Playwright (recorridos críticos)
```

## Servido y URLs
La SPA se sirve **desde la aplicación Spring Boot bajo el mismo origen**. La API está bajo `/api`. **No hay CORS** en producción — todo es same-origin. Sin SSR, sin GraphQL.

## Cliente de API
Generado a partir de la especificación OpenAPI (contract-first, ADR-0001). **No escribas servicios HTTP a mano** — actualiza la spec y regenera. Una prueba de contrato en CI verifica que el cliente generado coincide con el backend real.

## Autenticación
La sesión es una **cookie `httpOnly`** gestionada por el backend (ADR-0003). Implicaciones para el frontend:

- **Nunca leas, guardes ni envíes tokens desde JS** — el navegador adjunta la cookie sola.
- No uses `localStorage`/`sessionStorage` para nada relacionado con auth.
- El login es por **magic link** o contraseña; tras autenticarse el backend establece la cookie.
- En 401, redirige al login. En 403, muestra error de permisos (no des por hecho que basta con re-autenticar).

## Autorización en la UI
La autorización **real** la decide el backend (ADR-0009). En el frontend:

- **Oculta o deshabilita** acciones que el rol del usuario no puede ejecutar — UX, no seguridad.
- **Nunca asumas** que ocultar un botón impide la operación: el backend siempre re-comprueba.
- Roles disponibles: `admin`, `entrenador`, `alumno`.

## Lenguaje ubicuo
El vocabulario del dominio (`alumno`, `entrenador`, `grupo`, `plan`, `sesión`, `reporte`, `tag`) está en **español** en todo el código: nombres de componente, rutas, selectores, traducciones. El glosario autoritativo es `../docs/glosario.md`.

## Diseño visual
Las pantallas del camino crítico se diseñan primero como **maquetas HTML/CSS hi-fi** en `../docs/diseno/` (ver `editor-plan-semanal.html` como referencia). Cuando construyas un componente Angular, parte de la maqueta correspondiente — no inventes layout desde cero.

## Testing
- **Unit**: **Jest** (`jest.config.js`, `setup-jest.ts`) — `npm test`.
- **E2E**: **Playwright** (`playwright.config.ts`, ADR-0010), **solo recorridos críticos** del *loop* entrenador↔alumno. No probar todo de extremo a extremo.
- **Contrato**: cubierto por el pipeline backend; el frontend usa el cliente generado.

## Code style
- **ESLint** + **Prettier** en cada build.
- Componentes standalone por defecto.
- Lazy loading por ruta — evita imports estáticos de features no usados al arrancar.
- Cero `any` injustificados — si lo necesitas, comenta por qué.
