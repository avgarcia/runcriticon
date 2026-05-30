# Riesgos

> Lista viva. Revisar al cerrar la discovery y antes de cada release. Refleja el alcance **mono-club** del MVP.

Cada riesgo se valora con:
- **Impacto** (1 bajo, 3 alto)
- **Probabilidad** (1 baja, 3 alta)
- **Mitigación** prevista

> **Tras la revisión Nivel 1 del corpus de ADRs (mayo 2026)**: las mitigaciones técnicas viven en sub-decisiones concretas de los ADRs aceptados; este documento referencia esas sub-decisiones en lugar de duplicar el contenido. El [ADR-0015](adr/0015-temas-aplazados-fuera-del-mvp.md) es el **índice maestro consolidado de aplazamientos** con sus disparadores de reapertura.

---

## Riesgos de producto

### R1 — Scope creep en MVP

- **Impacto:** 3 · **Probabilidad:** 3
- **Descripción:** incluso con la acotación a un club, hay tentación de añadir mensajería, integraciones, plantillas, etc. antes de probar el bucle básico.
- **Mitigación:** los 12 MUST del [backlog](backlog.md) son intocables; cualquier extra cae a SHOULD por defecto.

### R2 — El modelo "plan por grupo" no encaja con cómo trabajan los entrenadores reales

- **Estado: CERRADO (2026-05-20).** La [validación de wireframes](wireframes/findings.md) lo confirma mitigado: RG y VG construyeron la semana de un grupo en < 7 min con "copiar semana anterior" + personalización en modal. VG: *"mi gran miedo era escribir el mismo plan 40 veces… me habéis solucionado la vida"*. El modelo plan-por-grupo encaja.

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

- **Impacto:** 2 · **Probabilidad:** 3 → **bajado a Probabilidad 2** tras la revisión Nivel 1 del corpus.
- **Descripción:** construir mono-club asume "1 club" en muchos lugares; pasar a multi-club después podría requerir reescritura si no se aísla bien.
- **Mitigación** (consolidada en ADRs aceptados):
  - **`club_id` en todas las tablas de dominio desde la primera migración** ([ADR-0006 D22](adr/0006-infraestructura-mono-tenant.md#d22)), aunque siempre valga el mismo valor en MVP.
  - **Filtro sistemático por `club_id` en repositorios** con aspecto `@AuthScope` ([ADR-0009 D4](adr/0009-modelo-de-autorizacion.md#d4), [ADR-0009 D11](adr/0009-modelo-de-autorizacion.md#d11)) — un fallo puntual no podría cruzar datos entre clubes.
  - **Aislamiento del supuesto "un club" en pocas capas** (resolución del principal, núcleo compartido de autorización) — [ADR-0009 D6](adr/0009-modelo-de-autorizacion.md#d6).
  - **Subdominio por club preparado en la estrategia de dominio** (`app.runcriticon.com` en MVP → `{slug}.runcriticon.com` al multi-club, [ADR-0006 D16](adr/0006-infraestructura-mono-tenant.md#d16)).
  - **Disparadores cuantitativos** para activar los componentes multi-tenant: Multi-AZ RDS al **segundo club o ~500 usuarios activos** ([ADR-0006 D10](adr/0006-infraestructura-mono-tenant.md#d10)), Spring Session en Redis al activar `min ≥ 2` ([ADR-0003 D10](adr/0003-autenticacion-invite-only.md#d10) + [ADR-0006 D4](adr/0006-infraestructura-mono-tenant.md#d4)). Consolidados como índice en [ADR-0015](adr/0015-temas-aplazados-fuera-del-mvp.md).

### R7 — Sin signup público, captación post-MVP es lenta

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** cuando se decida abrir a más clubes, no hay flujo de alta listo y construirlo no es trivial.
- **Mitigación:**
  - Asumido y aceptado: el paso a multi-club es un proyecto separado, no se intenta dejar "casi listo".
  - **Evolución prevista documentada**: solicitud de acceso + aprobación ([ADR-0003 D3](adr/0003-autenticacion-invite-only.md#d3) "Cuándo reabrir") — el propio usuario teclea sus datos en un formulario y el club los aprueba; mueve la mecanografía al usuario manteniendo el control.
  - Cruce en el índice maestro de aplazamientos de [ADR-0015](adr/0015-temas-aplazados-fuera-del-mvp.md) (sección Identidad y autorización).

---

## Riesgos legales y de cumplimiento

### R8 — RGPD por datos de salud

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** datos de entrenamiento, ritmo cardíaco e historial físico son datos de salud (categoría especial bajo RGPD Art. 9).
- **Mitigación** (consolidada en ADR-0014, ver sub-decisiones):
  - **Residencia UE** (`eu-west-1`) y mecanismos de transferencia internacional documentados (DPF + SCC contingencia) → [ADR-0014 D1-D2](adr/0014-proteccion-de-datos-rgpd.md#d1).
  - **Cifrado en reposo y en tránsito** → [ADR-0014 D3-D4](adr/0014-proteccion-de-datos-rgpd.md#d3).
  - **Categorización de datos en seis grupos** + **borrado mixto** (físico para PII primaria, anonimización para auditoría, caducidad pasiva para outbox y backups) → [ADR-0014 D5-D6](adr/0014-proteccion-de-datos-rgpd.md#d5).
  - **Propagación del borrado vía evento `AlumnoEliminado`** a todas las proyecciones locales → [ADR-0014 D7](adr/0014-proteccion-de-datos-rgpd.md#d7).
  - **Anonimización de IPs** (/24) en logs operativos + `userId` hasheado → [ADR-0014 D9](adr/0014-proteccion-de-datos-rgpd.md#d9).
  - **Política de retención por categoría**: PII activa hasta baja + 30 días, auditoría identidad 12 m, auditoría autorización 24 m, outbox 30 d, backups 30 d, logs operativos 90 d → [ADR-0014 D10](adr/0014-proteccion-de-datos-rgpd.md#d10).
  - **Derechos del interesado** (Arts. 15-22) atendidos en plazo de **1 mes** con runbooks → [ADR-0014 D11-D15](adr/0014-proteccion-de-datos-rgpd.md#d11).
  - **Base legal**: consentimiento explícito Art. 9.2.a con captura técnica (tabla `identidad.consentimiento` versionada) → [ADR-0014 D16, D18](adr/0014-proteccion-de-datos-rgpd.md#d16).
  - **Menores excluidos del MVP** con disparador concreto (declaración del club piloto) → [ADR-0014 D17](adr/0014-proteccion-de-datos-rgpd.md#d17).
  - **RAT obligatorio** (Art. 30) versionado en `docs/legal/rat.md` → [ADR-0014 D19](adr/0014-proteccion-de-datos-rgpd.md#d19).
  - **DPIA simplificado** antes del lanzamiento → [ADR-0014 D20](adr/0014-proteccion-de-datos-rgpd.md#d20).
  - **Sin DPO formal en MVP** con análisis documentado y disparadores para reabrir → [ADR-0014 D21](adr/0014-proteccion-de-datos-rgpd.md#d21).
  - **Subencargados nominales** (AWS, Postmark, GitHub) con DPA → [ADR-0014 D22](adr/0014-proteccion-de-datos-rgpd.md#d22).
  - **Responsable del tratamiento**: **Runcriticon S.L.**; el club es responsable de su uso interno (categorización jurídica final por asesoría legal) → [ADR-0014 D23](adr/0014-proteccion-de-datos-rgpd.md#d23).
  - **Notificación de brechas**: AEPD ≤ 72 h + comunicación a afectados si alto riesgo + runbook → [ADR-0014 D24-D26](adr/0014-proteccion-de-datos-rgpd.md#d24).
  - **Auditoría de accesos a datos sensibles + denegaciones** en módulo `auditoria` dedicado → [ADR-0009 D15-D17](adr/0009-modelo-de-autorizacion.md#d15).
- **Pendientes jurídicos** (no bloquean programar; sí bloquean la beta con datos reales): validación de la base legal, redacción de textos de consentimiento y política de privacidad, firma de los DPA, constitución formal de Runcriticon S.L., validación de DPIA y del análisis sin DPO → lista completa en [ADR-0014 §Pendientes jurídicos](adr/0014-proteccion-de-datos-rgpd.md#pendientes-jur%C3%ADdicos-no-resueltos-en-este-adr).

### R9 — Términos de servicio de Strava / Garmin

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** la integración con Strava (SHOULD) está sujeta a límites de API y cláusulas que pueden cambiar.
- **Mitigación:** la integración no entra en MVP; revisar TOS antes de integrar; alternativa: importación manual de FIT/GPX.

---

## Riesgos técnicos

### R10 — Email transaccional poco fiable

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** todas las invitaciones (entrenadores y alumnos) dependen del email. Si llegan a spam, se rompe la puesta en marcha del club.
- **Mitigación** (consolidada en ADR-0005):
  - **Postmark como proveedor**, elegido por entregabilidad por defecto frente a SES y SMTP propio → [ADR-0005 D1](adr/0005-email-transaccional.md#d1).
  - **Envío asíncrono vía outbox de Spring Modulith** (garantía at-least-once: usuario creado ⇒ email enviado, aunque Postmark esté caído) → [ADR-0005 D2](adr/0005-email-transaccional.md#d2).
  - **Aislamiento tras un puerto** en `domain` para poder migrar a SES sin tocar la aplicación → [ADR-0005 D3](adr/0005-email-transaccional.md#d3).
  - **Dominio propio autenticado** con SPF, DKIM y DMARC obligatorios desde el día 1 → [ADR-0005 D4, D6](adr/0005-email-transaccional.md#d4).
  - **Plantillas versionadas en código** (no en server-side de Postmark) para no acoplar → [ADR-0005 D7](adr/0005-email-transaccional.md#d7).
  - **Webhooks de rebote y queja monitorizados** con tabla de direcciones bloqueadas tras hard bounce o complaint → [ADR-0005 D9](adr/0005-email-transaccional.md#d9).
  - **Política de fallos cruzada al outbox**: 5 reintentos + DLQ + republicación admin + alarma → [ADR-0005 D10](adr/0005-email-transaccional.md#d10).
  - **Fallback funcional**: el admin o el entrenador puede copiar el enlace de invitación desde la UI y compartirlo manualmente (WhatsApp, en persona) si un email concreto no llega → [ADR-0005 D13](adr/0005-email-transaccional.md#d13).
  - **SLA de entrega < 3 min p95** (anclado al magic link de 15 min de [ADR-0003 D8](adr/0003-autenticacion-invite-only.md#d8)) → [ADR-0005 NFRs](adr/0005-email-transaccional.md#requisitos-no-funcionales).
  - **Migración a SES con disparador cuantitativo**: > 50 000 emails/mes sostenidos 2 meses **o** coste mensual de Postmark > 100 €/mes → [ADR-0005 D15](adr/0005-email-transaccional.md#d15).

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
- **Mitigación (decidida 2026-05-17, ampliada 2026-05-27)**:
  - ✅ **Modelo de datos del plan con ritmos relativos desde día 1**: cada sesión se guarda con `Ritmo` tipado, `Absoluto(segPorKm)` o `Relativo(referencia, deltaSegPorKm)` — ver ADR-0002. El modelo original `{tipo, valor}` con `pct_umbral`/`pct_marca` se descartó en mayo de 2026: los entrenadores piensan en *"10K + 10 s/km"*, no en porcentajes.
  - ✅ **Riesgo cerrado**: H5 se consolidó en ronda 2 informal con RG y VG (mayo 2026); los ritmos relativos a marcas pasan de COULD a MUST del MVP (M19 + M20 del backlog). Las marcas del corredor son privadas del alumno (módulo Seguimiento) y nadie más del club las ve.

### R16 — Volumen real de 500 alumnos rompe asunciones del modelo de grupos

- **Estado: MITIGADO (2026-05-17).** El card-sort lo confirmó (30-40% de micro-grupos en ambas muestras). La mitigación principal es el cambio a tags libres: el admin puede definir grupos tan amplios o tan finos como necesite. Como segunda capa, se ha añadido el **MUST M9b** (sugerencia de fusión de micro-grupos): el sistema avisa cuando un grupo tiene ≤ 2 alumnos o cuando dos grupos comparten ≥ 80% de membresía.
- **Sigue siendo conveniente revisar tras el lanzamiento**: si el admin del club piloto recibe demasiadas sugerencias de fusión, hay que ajustar el umbral.

---

## Riesgos derivados del cambio a tags libres (2026-05-17)

### R17 — Sin tags pre-cargados sensatos, el admin se atasca al inicio

- **Estado: MITIGADO (2026-05-20).** La [validación de wireframes](wireframes/findings.md) lo confirma: RG y VG completaron onboarding + editor de tags sin atascarse. La pre-carga de tags y carreras populares funcionó (VG: *"si me das la lista vacía me da un perezón increíble"*). Ajuste menor pendiente para alta fidelidad: botón "configurar más tarde" en el paso de grupos. La mitigación original (pre-carga + onboarding guiado) se mantiene.

### R18 — Constructor de filtros para crear grupos demasiado técnico para el admin

- **Estado: MITIGADO (2026-05-20).** La [validación de wireframes](wireframes/findings.md) lo confirma: RG (98 s) y VG (75 s) crearon un grupo de 2 condiciones en < 2 min, ninguno preguntó por sintaxis textual, ambos lo elogiaron (VG: *"tenía pánico a que fuera como programar una base de datos"*). **No hace falta diseñar las variantes B/C.** Único ajuste aplicado (cambio A1): indicador "en vivo" en la vista previa, porque ambos buscaron un botón "Aplicar".

### R19 — Barrera del lenguaje técnico para alumnos novatos *(nuevo, 2026-05-20)*

- **Impacto:** 2 · **Probabilidad:** 3
- **Descripción:** la validación con la alumna novata (AM) reveló que la jerga del running (Fartlek, RPE, rodaje regenerativo) y la escala RPE numérica bloquean a los alumnos de iniciación, mientras que los avanzados (AVG, PM) la devoran. Si la card "hoy" y el reporte no adaptan el lenguaje, el club pierde a los novatos — un segmento grande.
- **Mitigación:**
  - Escala RPE con etiquetas por sensación, no números abstractos (cambio A2, ya aplicado a wireframes).
  - Para alumnos con tag `nivel: iniciación`, priorizar en la card descripción simple (tiempo/acción) sobre ritmos exactos.
  - Glosario / tooltips de términos técnicos (registrado en `backlog.md` como COULD).
