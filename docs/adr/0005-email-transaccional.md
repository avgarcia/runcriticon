# ADR-0005 — Proveedor de email transaccional

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `risks.md` (R10 — email poco fiable rompe la puesta en marcha), ADR-0003 (autenticación invite-only), ADR-0006 (infraestructura)

## Contexto y problema

Todo el alta de usuarios depende del email: las **invitaciones** a entrenadores y alumnos (ADR-0003), la verificación de cuenta y el reseteo de contraseña. El journey de puesta en marcha del admin (`journeys/admin-setup.md`) se rompe si los emails no llegan o caen en spam — está registrado como **riesgo R10**.

Hay que elegir cómo se envía el email transaccional.

## Drivers de la decisión

- **Entregabilidad** ante todo: un email de invitación en spam es un usuario perdido.
- Volumen bajo en beta (decenas-cientos de emails), pero con picos en el alta masiva inicial del club.
- Coste contenido.
- Integración sencilla desde Spring Boot.
- Capacidad de **autenticar el dominio** (SPF, DKIM, DMARC) y de observar entregas/rebotes.

## Opciones consideradas

- **Opción A** — Amazon SES.
- **Opción B** — Proveedor especializado (Postmark, Resend).
- **Opción C** — Servidor SMTP propio.

### Opción A — Amazon SES

Servicio de email de AWS.

- 👍 Coste muy bajo por email.
- 👍 Encaja si la infraestructura acaba en AWS (ADR-0006 apunta a cloud tradicional).
- 👍 Soporta autenticación de dominio (SPF/DKIM/DMARC) y métricas de entrega/rebote.
- 👎 Configuración inicial más manual; arranca en *sandbox* (límites hasta solicitar salida).
- 👎 La entregabilidad por defecto es buena pero exige configurar bien el dominio — no es "enchufar y listo".

### Opción B — Postmark / Resend

Proveedores centrados en email transaccional.

- 👍 **Entregabilidad excelente por defecto** y reputación de IP cuidada — justo el driver principal (R10).
- 👍 Onboarding y plantillas muy sencillos; buena observabilidad.
- 👎 Coste por email mayor que SES (irrelevante al volumen de la beta).
- 👎 Un proveedor más, fuera de la nube principal.

### Opción C — SMTP propio

- 👍 Sin coste de proveedor.
- 👎 **Entregabilidad mala** sin un trabajo enorme de reputación de IP. Choca de frente con R10. Descartada.

## Decisión

**Opción A: Amazon SES**, con la **Opción B (Postmark/Resend) como plan de contingencia** documentado.

Dado que la infraestructura va a cloud tradicional con AWS como candidata principal (ADR-0006), SES es la opción coherente y de coste mínimo, y cubre la autenticación de dominio y la observabilidad necesarias. La entregabilidad de SES es suficiente **siempre que el dominio se configure correctamente**, que es la condición que se convierte en requisito de implementación.

Requisitos de implementación que esta decisión impone:

- **Autenticar el dominio desde el día 1**: SPF, DKIM y DMARC configurados y verificados antes del primer envío real.
- **Solicitar la salida del *sandbox*** de SES antes de la beta con el club piloto.
- **Monitorizar rebotes y quejas**: si la tasa se dispara, actuar.
- **Fallback funcional**: el admin puede copiar y compartir manualmente el enlace de invitación (ya previsto en ADR-0003) si un email concreto no llega.
- **Plantillas de email** simples y en texto claro, remitente reconocible (`@dominio-del-club-o-producto`).

**Criterio de reapertura**: si durante la beta la entregabilidad de SES resulta insuficiente pese al dominio bien configurado, se migra a **Postmark** (Opción B). La integración se aísla detrás de una interfaz de envío en el backend para que ese cambio sea de una sola pieza.

## Consecuencias

### Positivas

- Coste mínimo y coherencia con la nube elegida.
- Autenticación de dominio y métricas de entrega cubiertas.

### Negativas / coste asumido

- La buena entregabilidad no es automática: depende de configurar bien el dominio y de salir del *sandbox*. Es trabajo de setup que hay que hacer sí o sí.

### Riesgos y mitigaciones

- **Emails de invitación en spam** (R10) → dominio autenticado (SPF/DKIM/DMARC), remitente reconocible, monitorización de rebotes, y fallback de enlace manual.
- **Quedarse en sandbox el día del lanzamiento** → solicitar la salida con margen, semanas antes de la beta.
- **Lock-in de SES** → aislar el envío tras una interfaz en el backend; migrar a Postmark es cambiar una implementación.

## Notas

- Si ADR-0006 acabara eligiendo GCP o Azure en lugar de AWS, reconsiderar: en ese caso un proveedor neutral como Postmark pasa a ser la opción por defecto (no tiene sentido usar SES fuera de AWS).
- El email *de marketing* o newsletters queda fuera de este ADR — aquí solo email transaccional.
