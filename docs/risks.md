# Riesgos

> Lista viva. Revisar al cerrar la discovery y antes de cada release. Refleja el alcance **mono-club** del MVP.

Cada riesgo se valora con:
- **Impacto** (1 bajo, 3 alto)
- **Probabilidad** (1 baja, 3 alta)
- **Mitigación** prevista

---

## Riesgos de producto

### R1 — Scope creep en MVP

- **Impacto:** 3 · **Probabilidad:** 3
- **Descripción:** incluso con la acotación a un club, hay tentación de añadir mensajería, integraciones, plantillas, etc. antes de probar el bucle básico.
- **Mitigación:** los 12 MUST del [backlog](backlog.md) son intocables; cualquier extra cae a SHOULD por defecto.

### R2 — El modelo "plan por grupo" no encaja con cómo trabajan los entrenadores reales

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** si los entrenadores del club piensan en plan-por-alumno (no por grupo), el MVP les genera fricción en vez de ahorrarles tiempo.
- **Mitigación:** **validar el modelo "plan por grupo" en las entrevistas de discovery antes de empezar a construir**. Si no encaja, replantear.

### R3 — Personalización dentro del grupo insuficiente

- **Impacto:** 2 · **Probabilidad:** 3
- **Descripción:** si un alumno se lesiona o viaja, el entrenador necesita ajustarle el plan sin romper el del grupo. Si no es trivial, vuelve al WhatsApp.
- **Mitigación:** la personalización por alumno es **MUST (M12)** desde día 1, no SHOULD.

### R3b — Taxonomía nivel × distancia × carrera demasiado rígida o demasiado granular

- **Estado: CERRADO (2026-05-17).** El [card-sort con RG y VG](research/findings.md#cierre-del-card-sort-con-rg-y-vg) confirmó el riesgo: la taxonomía no encaja en cómo piensan los entrenadores. Decisión: se descarta la taxonomía rígida y se activa el modelo de **tags libres en MVP** (ver [`vision.md`](vision.md)). El riesgo queda resuelto por cambio de modelo; las nuevas consecuencias se rastrean en R17.

### R3c — El catálogo de carreras queda desactualizado

- **Impacto:** 2 · **Probabilidad:** 3
- **Descripción:** si el admin no mantiene el catálogo, los alumnos no pueden apuntar carrera y el modelo se rompe.
- **Mitigación:**
  - Aviso al admin cuando una carrera del catálogo está a < 30 días o ya ha pasado.
  - Plantilla con carreras populares precargadas (MMM, San Silvestre, etc.).
  - "Sin carrera objetivo" siempre disponible como fallback.

### R4 — Falta de monetización clara

- **Impacto:** 1 · **Probabilidad:** 3
- **Descripción:** menor impacto que antes porque el MVP es para un club concreto, no para captar mercado.
- **Mitigación:** validar disposición a pagar con el club piloto antes de extender a un segundo club.

---

## Riesgos del modelo mono-club (nuevos)

### R5 — Dependencia de un único cliente

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** si el club piloto se desvincula (cambio de junta directiva, mal feedback, desinterés), el proyecto se queda sin datos reales y sin tracción.
- **Mitigación:**
  - Identificar **2 clubes piloto** aunque solo se construya para uno, para tener plan B.
  - Mantener al menos al admin del club involucrado quincenalmente.

### R6 — Deuda técnica de mono-tenant al generalizar

- **Impacto:** 2 · **Probabilidad:** 3
- **Descripción:** construir mono-club asume "1 club" en muchos lugares; pasar a multi-club después puede requerir reescritura.
- **Mitigación:**
  - Diseñar el modelo de datos con `club_id` desde el día 1 aunque siempre valga el mismo.
  - Aislar el supuesto "un solo club" en una pocas capas (auth, scoping) que sean sustituibles.

### R7 — Sin signup público, captación post-MVP es lenta

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** cuando se decida abrir a más clubes, no hay flujo de alta listo y construirlo no es trivial.
- **Mitigación:** asumido y aceptado. El paso a multi-club es un proyecto separado, no se intenta dejar "casi listo".

---

## Riesgos legales y de cumplimiento

### R8 — RGPD por datos de salud

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** datos de entrenamiento, ritmo cardíaco e historial físico son datos de salud (categoría especial bajo RGPD).
- **Mitigación:**
  - Política de privacidad clara desde día 1.
  - Consentimiento informado en la primera entrada del alumno.
  - Cifrado en tránsito y en reposo.
  - Exportar y borrar datos a petición.
  - Convenio con el club (responsable y encargado del tratamiento).
  - Consultar con asesoría legal antes del primer alumno real.

### R9 — Términos de servicio de Strava / Garmin

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** la integración con Strava (SHOULD) está sujeta a límites de API y cláusulas que pueden cambiar.
- **Mitigación:** la integración no entra en MVP; revisar TOS antes de integrar; alternativa: importación manual de FIT/GPX.

---

## Riesgos técnicos

### R10 — Email transaccional poco fiable

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** todas las invitaciones (entrenadores y alumnos) dependen del email. Si llegan a spam, se rompe la puesta en marcha del club.
- **Mitigación:** proveedor profesional (Resend / Postmark / SES) con dominio autenticado (SPF, DKIM, DMARC) desde el primer release; link manual de invitación como fallback.

### R11 — Import CSV mal hecho rompe el alta masiva

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** el alta masiva de alumnos vía CSV es **el punto de fricción más alto del journey del admin**. Si falla, el admin abandona.
- **Mitigación:** validación previa con preview, reporte de errores línea a línea, posibilidad de re-importar idempotente.

---

## Riesgos de adopción

### R12 — Los entrenadores del club no adoptan la herramienta

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** si los entrenadores siguen prefiriendo Excel + WhatsApp, no hay datos para los alumnos y el sistema queda inerte.
- **Mitigación:** entrevista 1-a-1 con cada entrenador del club piloto **antes** de construir, no después. Conseguir compromiso explícito.

### R13 — Estacionalidad del club

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** el calendario de un club tiene picos (vuelta en septiembre, preparación de primavera). Lanzar fuera de esos picos limita la prueba.
- **Mitigación:** apuntar el lanzamiento a una ventana natural del club piloto.

---

## Riesgos detectados tras la primera ronda de entrevistas (2026-05-17)

### R14 — Target demasiado estrecho si excluimos novatos y élites

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** las entrevistas confirman que el novato puro sin entrenador (perfil LS) y el corredor élite ya en TrainingPeaks (perfil PC) **no son target**. El segmento queda reducido a *"amateur intermedio con entrenador de club"*. Si ese segmento es más pequeño de lo que pensamos, el producto puede tener un techo bajo.
- **Mitigación:**
  - Estimar el segmento con datos del club piloto (¿qué % de sus 500 alumnos encaja?).
  - Si tras la beta se observa demanda real desde élites, valorar pasarela hacia HRV / vatios / correlación de métricas como expansión, no como MVP.

### R15 — Sin "ritmos relativos por marcas" somos *"otro gestor de planes más"*

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** la hipótesis H5 (ver [`vision.md`](vision.md)) — *"un plan, ritmos por corredor"* — emerge en las entrevistas como el verdadero diferenciador frente a TrainingPeaks y a apps de club genéricas. Si no la abordamos, el producto puede ser técnicamente correcto pero indistinguible para entrenadores con volumen como RG.
- **Mitigación (decidida 2026-05-17)**:
  - ✅ **Modelo de datos del plan con ritmos relativos desde día 1**: cada sesión se guarda como `{tipo, valor}` (absoluto / % umbral / % marca). UI del MVP solo expone ritmos absolutos. Ver nota de arquitectura en [`vision.md`](vision.md). Esto deja el camino abierto sin pagar el coste de UI en MVP y sin reescritura futura.
  - Validar H5 con al menos un segundo entrenador antes de activar la UI.
  - Si se confirma como condición de adopción del club piloto, valorar promover el feature a SHOULD-prioritario.

### R16 — Volumen real de 500 alumnos rompe asunciones del modelo de grupos

- **Estado: MITIGADO (2026-05-17).** El card-sort lo confirmó (30-40% de micro-grupos en ambas muestras). La mitigación principal es el cambio a tags libres: el admin puede definir grupos tan amplios o tan finos como necesite. Como segunda capa, se ha añadido el **MUST M9b** (sugerencia de fusión de micro-grupos): el sistema avisa cuando un grupo tiene ≤ 2 alumnos o cuando dos grupos comparten ≥ 80% de membresía.
- **Sigue siendo conveniente revisar tras el lanzamiento**: si el admin del club piloto recibe demasiadas sugerencias de fusión, hay que ajustar el umbral.

---

## Riesgos derivados del cambio a tags libres (2026-05-17)

### R17 — Sin tags pre-cargados sensatos, el admin se atasca al inicio

- **Impacto:** 2 · **Probabilidad:** 3
- **Descripción:** el modelo de tags libres da total flexibilidad, pero si el admin abre la herramienta y se encuentra una pantalla vacía donde *"defina su taxonomía desde cero"*, es muy probable que la abandone. El coste de empezar a pensar en abstracto la jerga de su club es alto.
- **Mitigación:**
  - Pre-cargar un **set sensato de tags y valores** (nivel: iniciación/medio/medio-alto/alto · distancia: 1500m/5k/10k/media/maratón · objetivo: sin carrera + plantilla de carreras populares · terreno: asfalto/trail/pista · estado: activo/lesión/post-parto/descanso). El admin acepta, edita o borra.
  - Onboarding guiado del admin: el primer paso es revisar la taxonomía pre-cargada, no crear tags desde cero.
  - Permitir importar la taxonomía de otro club (post-MVP, cuando haya más de uno).

### R18 — Constructor de filtros para crear grupos demasiado técnico para el admin

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** crear un grupo es ahora *"objetivo = maratón valencia AND nivel ∈ {medio, medio-alto}"*. Si la UI parece SQL o un *query builder* de power-user, el admin no técnico (perfil de [`admin-club.md`](personas/admin-club.md)) se bloquea.
- **Mitigación:**
  - UI tipo **selectores con chips**: el admin elige tags y valores haciendo clic, no escribiendo. Vista previa instantánea de los alumnos que caen en el filtro.
  - Plantillas de grupos comunes ("todos los que preparan X", "todos los del nivel Y"). El admin parte de una plantilla y la afina.
  - Test de usabilidad con el admin del club piloto en wireframes antes de programar la pantalla.
