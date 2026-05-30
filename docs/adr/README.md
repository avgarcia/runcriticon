# Architecture Decision Records (ADR)

Registro de las decisiones de arquitectura de Runcriticon. Cada ADR documenta **una** decisión: su contexto, las opciones que se barajaron, la elegida y sus consecuencias.

## Por qué ADRs

- Las decisiones de arquitectura tienen consecuencias caras de revertir. Documentarlas evita repetir debates y ayuda a quien se incorpore al equipo.
- Viven en el repo, versionados y revisados por PR — igual que el resto de `docs/`.
- Formato: **MADR** (Markdown Any Decision Records), ligero. Plantilla en [`template.md`](template.md).

## Cómo escribir un ADR

1. Copia [`template.md`](template.md) a `NNNN-titulo-en-kebab-case.md` con el siguiente número libre.
2. Rellénalo. El título es una frase corta que nombra la decisión, no el problema (ej. *"Usar Postgres como base de datos"*, no *"¿Qué base de datos?"*).
3. Empieza en estado `Propuesto`. Pasa a `Aceptado` cuando se aprueba en la PR.
4. Un ADR **no se borra ni se reescribe** una vez aceptado. Si una decisión se revierte, se crea un ADR nuevo que la sustituye y se marca el viejo como `Reemplazado por ADR-NNNN`.

## Estados

| Estado      | Significado                                     |
|-------------|-------------------------------------------------|
| Propuesto   | Redactado, pendiente de aprobación.             |
| Aceptado    | Aprobado y vigente.                             |
| Reemplazado | Sustituido por un ADR posterior (enlazar cuál). |
| Obsoleto    | Ya no aplica, sin sustituto.                    |

## Índice de ADRs

| #                                                | Título                                                                      | Estado    | Fecha      |
|--------------------------------------------------|------------------------------------------------------------------------------|-----------|------------|
| [0001](0001-stack-aplicacion-web.md)             | Stack de la aplicación web: Spring Boot + Angular                            | Aceptado  | 2026-05-20 |
| [0002](0002-modelo-de-datos-tags.md)             | Modelo de datos del dominio: tags, ritmos relativos y marcas privadas        | Aceptado  | 2026-05-20 |
| [0003](0003-autenticacion-invite-only.md)        | Autenticación invite-only sin registro público                              | Aceptado  | 2026-05-20 |
| [0004](0004-base-de-datos-postgresql.md)         | Base de datos: PostgreSQL con un esquema por módulo                          | Aceptado  | 2026-05-20 |
| [0005](0005-email-transaccional.md)              | Proveedor de email transaccional                                            | Aceptado  | 2026-05-20 |
| [0006](0006-infraestructura-mono-tenant.md)      | Infraestructura: mono-tenant en AWS con `club_id` desde el día 1             | Aceptado  | 2026-05-20 |
| [0007](0007-monolito-modular.md)                 | Monolito modular                                                             | Aceptado  | 2026-05-20 |
| [0008](0008-arquitectura-hexagonal-y-ddd.md)     | Arquitectura hexagonal y DDD (aplicados con criterio)                        | Aceptado  | 2026-05-20 |
| [0009](0009-modelo-de-autorizacion.md)           | Modelo de autorización: RBAC + autorización a nivel de objeto                | Aceptado  | 2026-05-22 |
| [0010](0010-pipeline-ci-cd.md)                   | Pipeline de CI/CD                                                            | Aceptado  | 2026-05-22 |
| [0011](0011-observabilidad.md)                   | Observabilidad                                                               | Aceptado  | 2026-05-22 |
| [0012](0012-frontend-libreria-de-componentes.md) | Frontend: librería de componentes y estrategia de UI                         | Aceptado  | 2026-05-22 |
| [0013](0013-configuracion-y-secretos.md)         | Configuración y secretos en runtime                                          | Aceptado  | 2026-05-22 |
| [0014](0014-proteccion-de-datos-rgpd.md)         | Protección de datos y cumplimiento RGPD                                      | Aceptado  | 2026-05-22 |
| [0015](0015-temas-aplazados-fuera-del-mvp.md)    | Temas de arquitectura aplazados fuera del MVP                                | Propuesto | 2026-05-22 |
| [0016](0016-runtime-backend-graalvm.md)          | Runtime del backend: GraalVM (JIT vs imagen nativa)                          | Propuesto | 2026-05-27 |

> Este índice se actualiza a mano al añadir cada ADR. El sitio navegable se genera con **log4brains**: `npm run adr:preview` para verlo en local, y se publica en **GitHub Pages** de forma automática en cada cambio (workflow `.github/workflows/adr-site.yml`).

> Los ADR están en estado **Propuesto**: recogen decisiones encaminadas en discovery, wireframes y la revisión de arquitectura, pendientes de aprobación formal del equipo técnico cuando se constituya. Pasan a **Aceptado** al aprobarse.
