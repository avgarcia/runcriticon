# Backlog — Runcriticon (alcance: un único club)

> Priorización **MoSCoW** sobre el alcance acotado del MVP a **un solo club**. Los planes se publican a **grupos**, no a alumnos individuales. La columna *Criterio de aceptación* solo aplica a los **MUST**.

> Regla de oro: si una funcionalidad se puede sustituir por **WhatsApp + un Excel** durante 6 meses, **no es MUST**.

## Roles del MVP

- **Admin del club** (1 persona).
- **Entrenador del club** (N personas).
- **Alumno del club** (M personas).

No hay signup público: el admin del club seedea las cuentas.

## Modelo de grupos (resumen)

Los grupos se forman por el cruce **nivel × distancia × carrera objetivo**. La carrera objetivo se elige del catálogo del club (mantenido por el admin) y admite el valor especial "sin carrera". El sistema sugiere grupos automáticamente; el entrenador o admin puede ajustar la pertenencia. Detalle completo en [`vision.md`](vision.md#modelo-de-grupos-en-el-mvp-primera-aproximaci%C3%B3n-a-validar).

## MUST — MVP

### Bloque 1 — Estructura del club (admin)

| # | Funcionalidad | Criterio de aceptación de alto nivel |
|---|---|---|
| M1 | Login (email + Google) sin signup público | Cualquier usuario con cuenta previamente creada por el admin entra en < 30s. Quien no esté dado de alta no puede crear cuenta. |
| M2 | Alta de entrenador por el admin | El admin introduce nombre y email; el sistema envía invitación; el entrenador entra y pone contraseña en < 2 min. |
| M3 | Alta de alumno por el admin | Igual que M2 pero con rol alumno. |
| M4 | Catálogo de carreras del club | El admin crea / edita carreras (nombre + fecha + distancia) y archiva las pasadas. Los alumnos solo pueden elegir carreras del catálogo. |
| M5 | Clasificación del alumno (nivel × distancia × carrera) | El admin asigna a cada alumno **nivel** (iniciación/medio/medio-alto/alto), **distancia** (1500m/5k/10k/media/maratón) y **carrera objetivo** (del catálogo o "sin carrera") en < 30s. |
| M6 | Grupos sugeridos automáticamente | El sistema agrupa a los alumnos por la combinación nivel × distancia × carrera; el listado de grupos se mantiene vivo a medida que cambian las clasificaciones. |
| M7 | Ajuste manual de pertenencia a grupo | El entrenador o admin puede meter o sacar a un alumno de un grupo concreto sin cambiar su clasificación general (excepción que prevalece sobre la sugerencia). |
| M8 | Asignar entrenadores a grupos | El admin asigna 1+ entrenadores a cada grupo en < 30s. |
| M9 | Reclasificar alumno | El admin cambia nivel / distancia / carrera de un alumno en < 30s; el sistema recoloca al alumno en los nuevos grupos sin perder su historial. |

### Bloque 2 — Plan y ejecución (entrenador y alumno)

| # | Funcionalidad | Criterio de aceptación de alto nivel |
|---|---|---|
| M10 | Editor de sesión de entrenamiento | El entrenador crea una sesión (tipo, distancia o tiempo, ritmo objetivo, notas) en < 30s. |
| M11 | Publicar plan semanal **a un grupo** | El entrenador define las sesiones de la semana del grupo y las publica de una vez; todos los alumnos del grupo las ven al instante. |
| M12 | Personalizar una sesión para un alumno concreto | El entrenador puede ajustar una sesión para 1 alumno del grupo (ej. lesionado) sin afectar al resto. |
| M13 | Vista "hoy" del alumno | El alumno abre la app y en < 5s ve qué sesión tiene hoy con todos los detalles. |
| M14 | Marcar sesión como hecho / parcial / no hecho + nota libre | El alumno reporta en < 15s desde el móvil. |
| M15 | Vista de seguimiento del entrenador por grupo | El entrenador ve en una pantalla qué alumnos del grupo han cumplido la semana y cuáles fallan. |
| M16 | Vista de salud del club (admin) | El admin ve cuántos alumnos activos hay por grupo, qué grupos están sin entrenador y la última actividad reportada por grupo. |

## SHOULD — Fase 2 (post-MVP, primeras semanas tras lanzamiento)

- Plantillas de plan reutilizables ("Preparación 10k 8 semanas") a nivel de club.
- Comentarios del entrenador en cada sesión ejecutada por el alumno.
- Importación de Strava (solo lectura, comparar plan vs. realidad).
- Métricas básicas para el alumno (volumen semanal, racha, KM acumulados).
- Cambios de plan a posteriori (mover una sesión, descansar, lesión).
- Vista de calendario mensual del alumno (en MVP solo semanal).

## COULD — Fase 3+ (si la beta del club funciona)

- **Tags libres como modelo de grupos** (evolución natural de la taxonomía rígida del MVP). El admin define la taxonomía del club; los grupos pasan a ser **consultas** sobre tags. La base de datos ya está preparada para esto desde el MVP (ver [nota de arquitectura](vision.md#nota-de-arquitectura-dise%C3%B1a-con-tags-lanza-con-taxonom%C3%ADa)).
- **Multi-club** (el cambio mayor: convertir el mono-tenant en multi-tenant).
- Mensajería tipo chat (más allá de comentarios por sesión).
- Integración Garmin Connect / Polar Flow / Coros.
- App móvil nativa (iOS / Android).
- Notificaciones push.
- Panel de administración de plataforma (solo tiene sentido si hay multi-club).
- Dashboards avanzados de carga (TSS, ATL/CTL).

## WON'T — No en esta versión del producto

- Signup público de clubs, entrenadores o alumnos.
- Monetización (pagos, suscripciones, facturación).
- Marketplace abierto de entrenadores.
- IA generadora automática de planes.
- Otros deportes (triatlón, ciclismo, natación).
- Red social / feed público.

---

## Histórico de cambios

- _YYYY-MM-DD_ — Versión inicial creada durante la fase de discovery.
- _YYYY-MM-DD_ — **Acotación a mono-club**. Se eleva el rol admin a MVP, se introduce el concepto de grupos como unidad de asignación de planes, se descarta el signup público.
- 2026-05-16 — **Modelo de grupos taxonómico**. Los grupos pasan de "nombre libre" a "nivel × distancia × carrera". Se añaden MUST de catálogo de carreras, clasificación del alumno, agrupación automática y ajuste manual. Bloque 1 pasa de 5 a 9 funcionalidades.
