# Backlog — Runcriticon (alcance: un único club)

> Priorización **MoSCoW** sobre el alcance acotado del MVP a **un solo club**. Los planes se publican a **grupos**, no a alumnos individuales. La columna *Criterio de aceptación* solo aplica a los **MUST**.

> Regla de oro: si una funcionalidad se puede sustituir por **WhatsApp + un Excel** durante 6 meses, **no es MUST**.

## Roles del MVP

- **Admin del club** (1 persona).
- **Entrenador del club** (N personas).
- **Alumno del club** (M personas).

No hay signup público: el admin del club seedea las cuentas.

## Modelo de grupos (resumen)

Los grupos se forman como **consultas nombradas sobre tags libres** definidos por el admin del club. La taxonomía rígida (nivel × distancia × carrera) quedó refutada por el card-sort y se descartó como modelo del MVP. El sistema pre-carga un set sensato de tags (nivel, distancia, objetivo / catálogo de carreras, terreno, estado) que el admin puede editar o ampliar. Detalle completo en [`vision.md`](vision.md).

## MUST — MVP

### Bloque 1 — Estructura del club (admin)

| # | Funcionalidad | Criterio de aceptación de alto nivel |
|---|---|---|
| M1 | Login (email + Google) sin signup público | Cualquier usuario con cuenta previamente creada por el admin entra en < 30s. Quien no esté dado de alta no puede crear cuenta. |
| M2 | Alta de entrenador por el admin | El admin introduce nombre y email; el sistema envía invitación; el entrenador entra y pone contraseña en < 2 min. |
| M3 | Alta de alumno por el admin | Igual que M2 pero con rol alumno. |
| M4 | Definir la taxonomía del club (tags y valores) | El admin ve un set de tags pre-cargados (nivel, distancia, objetivo / catálogo de carreras, terreno, estado), puede editarlos, añadir tags propios y editar la lista de valores de cada tag. El catálogo de carreras es un caso particular: valores del tag `objetivo` con metadata de fecha y distancia. |
| M5 | Asignar tags a un alumno | El admin asigna N tags a cada alumno en < 30s. Un alumno puede tener varios valores del mismo tag (ej. `objetivo = maratón valencia + mantenimiento`). |
| M6 | Crear grupo como consulta sobre tags | El admin o entrenador crea un grupo escribiendo un filtro sobre tags (ej. *"objetivo = maratón valencia AND nivel ∈ {medio, medio-alto}"*), le pone nombre y queda guardado. La membresía se calcula al instante y se mantiene viva. |
| M7 | Ajuste manual de pertenencia a grupo | El entrenador o admin mete o saca a un alumno de un grupo concreto sin tocar sus tags (excepción que prevalece sobre la query). |
| M8 | Asignar entrenadores a grupos | El admin asigna 1+ entrenadores a cada grupo en < 30s. |
| M9 | Editar tags de un alumno | El admin cambia los tags de un alumno en < 30s; el sistema actualiza su pertenencia a los grupos vivos sin perder el historial del alumno ni los planes ya publicados (snapshot). |
| M9b | Sugerencia de fusión de micro-grupos | Si dos grupos comparten ≥ 80% de alumnos o si un grupo tiene ≤ 2 alumnos, el sistema avisa al admin y propone fusionar o generalizar el filtro. Mitiga R16. |

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
| M17 | Panel de alertas del entrenador (feedback por excepción) | El entrenador entra y ve **solo** las alertas accionables: alumnos con molestias reportadas, alumnos sin reportar > 7 días, sesiones reportadas muy por debajo o por encima del objetivo. No muestra los entrenos normales. Origen: P2 en [findings](../research/findings.md) — RG y PC lo piden. |
| M18 | Reajuste de día por el alumno (imprevistos) | El alumno marca con 1 click "hoy no puedo / estoy cansado / molestias" y la sesión se mueve / se marca como salto, sin depender de respuesta del entrenador. Origen: P3 en findings — JM y AVG. |
| M19 | Ritmos relativos a las marcas del alumno | El entrenador expresa el ritmo de una sesión como delta sobre una distancia estándar (ej. *"10K + 10 s/km"*, *"42K − 5 s/km"*). Cada alumno ve el ritmo absoluto calculado desde su marca en esa distancia. Si el alumno no tiene marca para la distancia referenciada, ve el ritmo sin calcular y un CTA para introducirla. Origen: H5 validada con RG y VG en ronda 2 de wireframes. |
| M20 | Marcas privadas del alumno | El alumno introduce y mantiene sus marcas en 5K, 10K, 21K y 42K. Solo el alumno las ve — ni el entrenador ni el admin acceden a ellas. Se solicitan al activar la cuenta (onboarding). Las marcas viven en el módulo Seguimiento y no se filtran hacia otras capas. |

## SHOULD — Fase 2 (post-MVP, primeras semanas tras lanzamiento)

> **Prioridad #1 de esta fase** señalada con ★, en base a los hallazgos de la primera ronda de entrevistas.

- ★ **Importación de actividad del reloj** (FIT/GPX o Strava/Garmin Connect). Solo lectura, para comparar plan vs. realidad y alimentar el panel de alertas (M17). Pedido por JM y PC; usado por RG como input.
- ★ Comentarios contextuales del entrenador en cada sesión ejecutada por el alumno (no solo "comentario general"; idealmente por intervalo). Pedido por PC.
- Plantillas de plan reutilizables ("Preparación 10k 8 semanas") a nivel de club. Pedido por VG.
- Métricas básicas para el alumno (volumen semanal, racha, KM acumulados).
- Cambios de plan a posteriori por el entrenador (mover sesiones de varios días, ajuste de bloque por lesión).
- Vista de calendario mensual del alumno (en MVP solo semanal).

## COULD — Fase 3+ (si la beta del club funciona)

- ~~**Ritmos relativos a marcas del corredor**~~ → **Movido a MVP** como M19+M20 tras la validación informal con RG y VG en ronda 2 de wireframes (2026-05-27). Pendiente validar el sweet spot diferencial con un segundo entrenador ajeno al piloto.
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
- 2026-05-17 — **Ajustes tras la primera ronda de entrevistas**. Añadidos M17 (panel de alertas por excepción) y M18 (reajuste de día por el alumno). En SHOULD se prioriza ★ la importación de actividad del reloj y los comentarios contextuales. En COULD se añade *Ritmos relativos a marcas del corredor* como posible diferenciador (H5). Total MUST: 18.
- 2026-05-17 — **Tags libres en MVP tras el card-sort con RG y VG**. La taxonomía rígida nivel × distancia × carrera fue refutada parcialmente; se activa el plan B antes de programar. Bloque 1 reescrito: M4 pasa de "catálogo de carreras" a "definir taxonomía del club (tags y valores)" (el catálogo de carreras es ahora un tag pre-cargado), M5 pasa de "clasificación 3-ejes" a "asignar tags al alumno", M6 pasa de "grupos sugeridos automáticamente" a "crear grupo como consulta sobre tags", M9 pasa de "reclasificar" a "editar tags". Añadido M9b (sugerencia de fusión de micro-grupos) para neutralizar R16. *Tags libres* sale de COULD (ya está en MVP). Total MUST: 19.
- 2026-05-27 — **Ritmos relativos y marcas del alumno al MVP** tras la ronda 2 de validación de wireframes con RG y VG. Añadidos M19 (ritmos expresados como delta sobre distancia estándar, no porcentajes) y M20 (marcas privadas del alumno — solo el alumno las ve). H5 confirmada. "Ritmos relativos" sale de COULD. Total MUST: 21.
