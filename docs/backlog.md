# Backlog — Runcriticon

> Priorización **MoSCoW** del universo de funcionalidades. La columna *Criterio de aceptación* solo aplica a los **MUST** (el resto se desarrollará cuando se priorice). Versión inicial; refinar tras entrevistas.

> Regla de oro: si una funcionalidad se puede sustituir por **WhatsApp + un Excel** durante 6 meses, **no es MUST**.

## MUST — MVP (entrenador + corredor, web responsive)

| # | Funcionalidad | Criterio de aceptación de alto nivel |
|---|---|---|
| M1 | Registro / login de entrenador y corredor (email + Google) | Un usuario nuevo crea cuenta en < 60s y elige su rol durante el onboarding. |
| M2 | Invitación entrenador → corredor (vínculo 1 a N) | El entrenador envía una invitación por email; el corredor entra con el enlace y queda vinculado a ese entrenador. |
| M3 | Editor de sesión de entrenamiento | El entrenador crea una sesión con tipo (rodaje / series / fondo / tirada larga / descanso / otro), distancia o tiempo, ritmo objetivo y notas en < 30s. |
| M4 | Vista semanal / mensual del plan del corredor | El corredor abre la app y en < 5s sabe qué tiene hoy y qué le queda esta semana. |
| M5 | Marcar sesión como hecho / parcial / no hecho + nota libre | El corredor reporta una sesión en < 15s desde el móvil. |
| M6 | Vista de seguimiento del entrenador | El entrenador ve en una sola pantalla qué corredor ha cumplido la semana y qué corredor está fallando. |

## SHOULD — Fase 2 (post-MVP, primeras semanas tras lanzamiento)

- Plantillas de plan reutilizables ("Preparación 10k 8 semanas").
- Comentarios del entrenador en cada sesión ejecutada.
- Importación de Strava (solo lectura, comparar plan vs. realidad).
- Métricas básicas para el corredor (volumen semanal, racha, KM acumulados).
- Cambios de plan a posteriori (mover una sesión, descansar, lesión).

## COULD — Fase 3+ (cuando haya tracción)

- Rol Club / equipo + vista agregada.
- Mensajería tipo chat (más allá de comentarios por sesión).
- Integración Garmin Connect / Polar Flow / Coros.
- App móvil nativa (iOS / Android).
- Notificaciones push de recordatorio.
- Panel de administración interno.
- Dashboards avanzados de carga (TSS, ATL/CTL, riesgo de lesión).

## WON'T — No en esta versión del producto

- Monetización (pagos, suscripciones, facturación).
- Marketplace abierto de entrenadores.
- IA generadora automática de planes.
- Otros deportes (triatlón, ciclismo, natación).
- Red social / feed público.

---

## Histórico de cambios

- _YYYY-MM-DD_ — Versión inicial creada durante la fase de discovery.
