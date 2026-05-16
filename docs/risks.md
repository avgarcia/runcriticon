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

- **Impacto:** 3 · **Probabilidad:** 3
- **Descripción:** la taxonomía 4 niveles × 5 distancias × N carreras puede producir muchos micro-grupos (de 1 o 2 alumnos) y obligar a fusionar manualmente, o al revés: que los entrenadores no piensen así realmente y la clasificación no se mantenga al día.
- **Mitigación:**
  - El **ajuste manual de pertenencia (M7)** es MUST: la taxonomía sugiere, el humano decide.
  - **Modelo de datos basado en tags desde día 1** (ver [nota de arquitectura](vision.md#nota-de-arquitectura-dise%C3%B1a-con-tags-lanza-con-taxonom%C3%ADa)): aunque la UI solo expone 3 ejes, internamente todo se guarda como tags clave-valor. Esto permite añadir ejes nuevos o pasar a tags libres sin reescribir la base de datos.
  - Validar la taxonomía concreta en entrevistas (H4): ¿realmente piensan en estos 3 ejes con estos valores? ¿Hay más / menos niveles?
  - Considerar añadir "duración del bloque de preparación" como cuarto eje si emerge en entrevistas.
  - Marcar el modelo como "primera aproximación" en la documentación y revisarlo a los 3 meses de beta.

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
