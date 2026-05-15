# Runcriticon

Aplicación para que un **club de running amateur** gestione los entrenos de sus grupos: el admin del club organiza entrenadores y alumnos por grupos; los entrenadores publican planes semanales al grupo; los alumnos siguen el plan y reportan.

> **Alcance del MVP**: **un único club**. No es multi-tenant. Los usuarios se dan de alta por el admin del club (no hay signup público). Los planes se asignan a **grupos**, no a alumnos individuales.

> Estado actual: **fase de discovery**. Aún no hay código. La documentación de esta carpeta `docs/` se irá rellenando durante las 2 semanas de descubrimiento del producto.

## Estructura de la documentación

- [`docs/vision.md`](docs/vision.md) — visión, alcance mono-club y objetivos.
- [`docs/personas/`](docs/personas/) — admin del club, entrenador, alumno (MVP) y admin de plataforma (post-MVP).
- [`docs/research/`](docs/research/) — guiones y notas de entrevistas.
- [`docs/journeys/admin-setup.md`](docs/journeys/admin-setup.md) — puesta en marcha del club por el admin.
- [`docs/journeys/coach-runner.md`](docs/journeys/coach-runner.md) — bucle semanal entrenador ↔ alumno a través de grupos.
- [`docs/backlog.md`](docs/backlog.md) — funcionalidades priorizadas (MoSCoW).
- [`docs/risks.md`](docs/risks.md) — riesgos identificados.

El plan completo de la fase de discovery está en `~/.claude/plans/c-mo-persona-de-negocio-foamy-piglet.md`.
