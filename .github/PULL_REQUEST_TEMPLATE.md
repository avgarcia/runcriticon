<!-- Plantilla de PR (ADR-0010 D20: merge commits para PRs aprobadas) -->

## Resumen

<!-- 1-2 frases. Qué cambia y por qué. -->

## Cambios

<!-- Lista de archivos/áreas tocadas. Si aplica, cruce a ADR-XXXX DN. -->

## Checklist

- [ ] He leído los ADRs relevantes y la guía [`docs/arquitectura/estructura-de-un-modulo.md`](../docs/arquitectura/estructura-de-un-modulo.md).
- [ ] Si introduce un módulo o cambia su contrato: actualizo `RGPD.md`, `CONFIG.md`, `OBSERVABILIDAD.md`, `README.md` del módulo (los que apliquen).
- [ ] Si introduce un secreto nuevo: añadido al catálogo (ADR-0013 D6) + runbook de rotación en `docs/runbooks/`.
- [ ] Si introduce un evento de integración: JSON Schema en `schemas/{modulo}/{evento}-v{N}.json`.
- [ ] Si toca una tabla con datos personales: categoría declarada (`@CategoriaRGPD` + comentario SQL).
- [ ] Tests: unitarios, integración con Testcontainers, ArchUnit, acceso cruzado por caso de uso si aplica.
- [ ] CI verde (lint, tests, ArchUnit, secret scan, SAST).
- [ ] Si modifica un ADR o invalida una premisa heredada: PR de cambio del ADR encadenada.

## Estado

<!-- Propuesto / WIP / Listo para revisar -->
