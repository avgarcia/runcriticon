# Riesgos

> Lista viva. Revisar al cerrar la discovery y antes de cada release.

Cada riesgo se valora con:
- **Impacto** (1 bajo, 3 alto)
- **Probabilidad** (1 baja, 3 alta)
- **Mitigación** prevista

---

## Riesgos de producto

### R1 — Scope creep en MVP

- **Impacto:** 3 · **Probabilidad:** 3
- **Descripción:** el alcance inicial verbalizado (4 roles + web + móvil nativa + Strava/Garmin + chat) es demasiado para un primer lanzamiento.
- **Mitigación:** congelar MUST a 6 funcionalidades; cualquier cambio durante implementación cae a SHOULD/COULD por defecto.

### R2 — Falta de monetización clara

- **Impacto:** 2 · **Probabilidad:** 3
- **Descripción:** sin modelo de negocio decidido, el equipo puede construir features que no sostengan el producto.
- **Mitigación:** durante discovery, validar disposición a pagar (entrenadores) sin comprometer el MVP. Revisar tras 3 meses de beta.

### R3 — Que sea "otro TrainingPeaks pero peor"

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** sin un diferenciador claro (idioma, simplicidad, precio, flujo) no hay razón para que los usuarios cambien.
- **Mitigación:** definir en `docs/vision.md` el diferenciador en 1 frase, contrastado en entrevistas.

---

## Riesgos legales y de cumplimiento

### R4 — RGPD por datos de salud

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** los datos de entrenamiento, ritmo cardíaco e historial físico son datos de salud (categoría especial bajo RGPD). El consentimiento debe ser explícito y los datos protegidos adecuadamente.
- **Mitigación:**
  - Política de privacidad clara desde día 1.
  - Consentimiento informado en el onboarding.
  - Cifrado en tránsito y en reposo.
  - Posibilidad de exportar y borrar datos.
  - Consultar con asesoría legal antes del lanzamiento público.

### R5 — Términos de servicio de Strava / Garmin

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** la integración con Strava está sujeta a límites de API y cláusulas comerciales que pueden cambiar.
- **Mitigación:** aplazar la integración a SHOULD; revisar TOS antes de integrar; tener un plan de contingencia (importación manual de FIT/GPX).

---

## Riesgos técnicos (preliminares, a revisar tras decisión de stack)

### R6 — Dependencia de proveedores cloud / autenticación

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** elegir un proveedor que encarezca rápido al escalar.
- **Mitigación:** evaluar coste a 1k y 10k usuarios antes de decidir stack.

### R7 — Email transaccional poco fiable

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** las invitaciones entrenador → corredor dependen del email. Si llegan a spam, se rompe el journey.
- **Mitigación:** proveedor profesional (Resend / Postmark / SES) con dominio autenticado (SPF, DKIM, DMARC) desde el primer release; link manual de invitación como fallback.

---

## Riesgos de mercado y captación

### R8 — No conseguir los primeros 10 entrenadores beta

- **Impacto:** 3 · **Probabilidad:** 2
- **Descripción:** sin entrenadores activos no hay corredores y no hay pruebas reales.
- **Mitigación:** identificar y comprometer 3-5 entrenadores **durante** la discovery, no después. Confirmados los contactos disponibles, validar si quieren ser beta.

### R9 — Estacionalidad del running

- **Impacto:** 2 · **Probabilidad:** 2
- **Descripción:** picos en septiembre (vuelta al cole, preparación de carreras de otoño) y febrero (preparación de carreras de primavera). Lanzar en mal momento limita la tracción.
- **Mitigación:** apuntar lanzamiento beta a una ventana de captación natural.
