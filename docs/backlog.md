# Backlog — Runcriticon (alcance: un único club)

> Priorización **MoSCoW** sobre el alcance acotado del MVP a **un solo club**. Los planes se publican a **grupos**, no a alumnos individuales. La columna *Criterio de aceptación* solo aplica a los **MUST**.

> Regla de oro: si una funcionalidad se puede sustituir por **WhatsApp + un Excel** durante 6 meses, **no es MUST**.

## Roles del MVP

- **Admin del club** (1 persona).
- **Entrenador del club** (N personas).
- **Alumno del club** (M personas).

No hay signup público: el admin del club seedea las cuentas.

## MUST — MVP

### Bloque 1 — Estructura del club (admin)

| # | Funcionalidad | Criterio de aceptación de alto nivel |
|---|---|---|
| M1 | Login (email + Google) sin signup público | Cualquier usuario con cuenta previamente creada por el admin entra en < 30s. Quien no esté dado de alta no puede crear cuenta. |
| M2 | Alta de entrenador por el admin | El admin introduce nombre y email; el sistema envía invitación; el entrenador entra y pone contraseña en < 2 min. |
| M3 | Alta de alumno por el admin | Igual que M2 pero con rol alumno. |
| M4 | Crear / editar grupo de entrenamiento | El admin crea un grupo con nombre y descripción opcional, asigna 1+ entrenadores y N alumnos en < 1 min. |
| M5 | Reasignar alumno entre grupos | El admin mueve un alumno de "iniciación" a "avanzados" en < 30s sin perder su historial. |

### Bloque 2 — Plan y ejecución (entrenador y alumno)

| # | Funcionalidad | Criterio de aceptación de alto nivel |
|---|---|---|
| M6 | Editor de sesión de entrenamiento | El entrenador crea una sesión (tipo, distancia o tiempo, ritmo objetivo, notas) en < 30s. |
| M7 | Publicar plan semanal **a un grupo** | El entrenador define las sesiones de la semana del grupo y las publica de una vez; todos los alumnos del grupo las ven al instante. |
| M8 | Personalizar una sesión para un alumno concreto | El entrenador puede ajustar una sesión para 1 alumno del grupo (ej. lesionado) sin afectar al resto. |
| M9 | Vista "hoy" del alumno | El alumno abre la app y en < 5s ve qué sesión tiene hoy con todos los detalles. |
| M10 | Marcar sesión como hecho / parcial / no hecho + nota libre | El alumno reporta en < 15s desde el móvil. |
| M11 | Vista de seguimiento del entrenador por grupo | El entrenador ve en una pantalla qué alumnos del grupo han cumplido la semana y cuáles fallan. |
| M12 | Vista de salud del club (admin) | El admin ve cuántos alumnos activos hay por grupo y la última actividad reportada por grupo. |

## SHOULD — Fase 2 (post-MVP, primeras semanas tras lanzamiento)

- Plantillas de plan reutilizables ("Preparación 10k 8 semanas") a nivel de club.
- Comentarios del entrenador en cada sesión ejecutada por el alumno.
- Importación de Strava (solo lectura, comparar plan vs. realidad).
- Métricas básicas para el alumno (volumen semanal, racha, KM acumulados).
- Cambios de plan a posteriori (mover una sesión, descansar, lesión).
- Vista de calendario mensual del alumno (en MVP solo semanal).

## COULD — Fase 3+ (si la beta del club funciona)

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
