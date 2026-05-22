# ADR-0014 — Protección de datos y cumplimiento RGPD

- **Estado**: Propuesto
- **Fecha**: 2026-05-22
- **Decisores**: Negocio (Antonio) · futuro equipo técnico · **asesoría legal** (para los pendientes jurídicos)
- **Relacionado con**: ADR-0004 (base de datos), ADR-0005 (email — Postmark), ADR-0006 (infraestructura, región), ADR-0007 (monolito modular, events-first), ADR-0009 (autorización y auditoría), ADR-0013 (secretos)

## Contexto y problema

Runcriticon trata **datos personales y de salud sensibles** de personas reales en España. Seis ADR citan el RGPD como *driver*, pero **ninguna decisión de protección de datos estaba registrada** — la auditoría de arquitectura lo identificó como el hueco de mayor prioridad. Hay que fijar las decisiones técnicas de protección de datos.

> **Alcance de este ADR.** Recoge las decisiones **técnicas y de arquitectura**. Las decisiones **jurídicas** —base legal del tratamiento, textos de consentimiento, firma de los acuerdos de encargado— **requieren asesoría legal** y quedan recogidas en "Pendientes jurídicos", **no resueltas aquí**.

## Drivers de la decisión

- Datos de salud sensibles + usuarios en España → el RGPD aplica de lleno.
- **Responsabilidad proactiva**: poder demostrar el cumplimiento.
- Equipo de 4 personas y MVP → soluciones **proporcionadas**.
- Coherencia con la infraestructura (ADR-0006), la base de datos (ADR-0004) y la comunicación *events-first* (ADR-0007).

## Decisión

### Residencia del dato

Todo el tratamiento de datos se hace en la **UE**: región AWS **`eu-west-1` (Irlanda)**. Cubre la residencia UE/EEE que el RGPD exige, tiene App Runner (ADR-0006) y es la región más madura. Se descartó `eu-south-2` (España) por el riesgo de que App Runner no esté disponible — y el RGPD **no exige España**, basta la UE/EEE.

El email transaccional usa **Postmark** (ADR-0005), empresa de EE. UU.: exige firmar su **DPA** y apoyarse en un mecanismo de transferencia (el *Data Privacy Framework* UE-EE. UU. o cláusulas contractuales tipo) — ver "Pendientes jurídicos".

### Cifrado

- **En reposo**: cifrado de Amazon RDS activado (cubre también los *backups*); los secretos en SSM Parameter Store como `SecureString` (ADR-0013).
- **En tránsito**: HTTPS en todo el tráfico; conexión cifrada con la base de datos.

### Derecho de supresión

Al ejercerse el derecho de supresión, **borrado físico** de todos los datos del alumno. Un evento de dominio (`AlumnoEliminado`) propaga el borrado a las **proyecciones locales de todos los módulos** (*events-first*, ADR-0007). Los *backups* conservan el dato hasta que caducan según su retención acotada — admisible bajo el RGPD si la retención está limitada y no se restaura selectivamente para resucitar datos borrados.

Se descartó la **anonimización**: conserva analítica agregada que el MVP no necesita, y una anonimización mal hecha (dato residual reidentificable) **incumpliría** el RGPD.

### Derechos de acceso y portabilidad

Se atienden con un **procedimiento manual documentado** (un *runbook* para el admin) — a la escala de un club las solicitudes serán contadísimas, y el MVP no presupuesta una funcionalidad de exportación. El *runbook* debe evitar el acceso descontrolado a la base de datos de producción. La **funcionalidad de exportación** queda como mejora posterior; el modelo de datos relacional y acotado por usuario hace que añadirla sea sencillo.

### Minimización de datos

Solo se recogen los datos necesarios para prestar el servicio. La revisión concreta de qué campos se recogen es parte del diseño de cada funcionalidad y del análisis jurídico.

### Auditoría de accesos

Ya decidida en **ADR-0009**: registro *append-only* de accesos denegados y de accesos a datos sensibles. Cubre la responsabilidad proactiva.

### Subencargados del tratamiento

Los subencargados que tratan datos personales —**AWS** (alojamiento, base de datos) y **Postmark** (email)— requieren un **acuerdo de encargado del tratamiento (DPA)** firmado. Es un trámite jurídico/de contratación — ver "Pendientes jurídicos".

### Notificación de brechas

El RGPD obliga a notificar una brecha de datos a la autoridad de control en **72 horas**. El procedimiento de respuesta a incidentes incluye esa notificación y se documenta como *runbook*.

## Pendientes jurídicos (no resueltos en este ADR)

Requieren **asesoría legal** antes del lanzamiento con el club piloto:

- Base legal del tratamiento de datos de salud (y de menores, si los hubiera).
- Textos de información y consentimiento a los usuarios.
- Firma de los DPA con AWS y con Postmark.
- Validación de la política de retención de datos y *backups* con criterio legal.

## Consecuencias

### Positivas

- Las decisiones de protección de datos dejan de estar implícitas — quedan registradas y son auditables.
- Residencia UE, cifrado en reposo y en tránsito, y borrado físico dan una base de cumplimiento sólida.
- Coste y esfuerzo proporcionados al MVP.

### Negativas / coste asumido

- El borrado físico renuncia a la analítica histórica de ex-miembros.
- Acceso y portabilidad manuales — trabajo en cada solicitud (asumible por el volumen esperado).
- Quedan **pendientes jurídicos** que el equipo técnico no puede cerrar por sí solo.

### Riesgos y mitigaciones

- **Lanzar con los pendientes jurídicos sin cerrar** → no lanzar con el club piloto hasta resolverlos con asesoría legal.
- **Borrado incompleto entre módulos** (events-first) → el evento `AlumnoEliminado` y tests que verifiquen que cada proyección local se purga.
- **Datos en región equivocada** → la región `eu-west-1` se fija en la IaC (Terraform, ADR-0006); revisión de que ningún recurso con datos se crea fuera de la UE.

## Notas

- La **analítica histórica anonimizada** de ex-miembros puede reabrirse como decisión posterior si el negocio la necesita — con un análisis de reidentificación serio.
- La **funcionalidad de exportación** de datos del usuario es una mejora posterior, no del MVP.
- Si se necesitara residencia **estrictamente** europea también para el email, habría que revisar ADR-0005 (Postmark).
