# ADR-0014 — Protección de datos y cumplimiento RGPD

- **Estado**: Aceptado
- **Fecha**: 2026-05-22 · revisado 2026-05-29 (reorganización Nivel 1: premisas heredadas, NFRs propios, sub-decisiones numeradas D1-D26 con anchors; **corrección de la posición sobre anonimización**: se sustituye "anonimización descartada" por el **patrón de borrado mixto** que el resto de la arquitectura ya asume — coherencia con ADR-0009 D17; incorporación de: categorización explícita de datos en seis grupos, política de retención por categoría, base legal de datos de salud, tratamiento de menores, captura técnica del consentimiento, RAT, DPIA simplificado, DPO no formal con análisis, lista nominal de subencargados, responsable del tratamiento, runbook de respuesta a brechas, anonimización de IPs en logs operativos) · **aceptado 2026-05-29** · revisado 2026-06-12 (corrección de drift de nombres de módulo/esquema: `salud.*` → `seguimiento.*` en los ejemplos de la categoría 1 y "módulo (de) Salud" → módulo Seguimiento — esquemas canónicos de ADR-0004 D4; sin cambio de decisión)
- **Decisores**: Negocio (Antonio) · futuro equipo técnico · **asesoría legal** (para los pendientes jurídicos)
- **Relacionado con**: ADR-0003 (autenticación, auditoría de identidad), ADR-0004 (base de datos), ADR-0005 (email — Postmark, reglas RGPD sobre contenido), ADR-0006 (infraestructura, región), ADR-0007 (monolito modular, events-first, outbox, retención), ADR-0008 (hexagonal, `Result<T, DomainError>`), ADR-0009 (autorización, auditoría de accesos, anonimización al olvido), ADR-0010 (CI/CD, observabilidad, postmortem), ADR-0013 (secretos)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre protección de datos. Las veintiséis sub-decisiones se agrupan en ocho áreas:

- **Residencia y transferencias (D1-D2)** — dónde viven los datos y cómo cruzan fronteras.
- **Cifrado (D3-D4)** — en reposo y en tránsito.
- **Categorización y borrado mixto (D5-D9)** — el núcleo de este ADR: seis categorías de datos con su política de borrado al ejercer el olvido.
- **Política de retención (D10)** — tabla con duración y disparador de purga por categoría.
- **Derechos del interesado (D11-D15)** — Arts. 15-22 del RGPD: acceso, rectificación, supresión, oposición, portabilidad.
- **Base legal y categorías especiales (D16-D18)** — base legal de datos de salud (Art. 9), menores, captura técnica del consentimiento.
- **Gobierno y demostrabilidad (D19-D22)** — RAT (Art. 30), DPIA (Art. 35), DPO (Art. 37), subencargados.
- **Responsable del tratamiento (D23)** — quién es el responsable a efectos del RGPD.
- **Notificación de brechas (D24-D26)** — Arts. 33-34: notificación a la AEPD y a los afectados.

| #   | Sub-decisión                                                                       | Capa         |
|-----|------------------------------------------------------------------------------------|--------------|
| D1  | [Residencia: AWS `eu-west-1` (Irlanda)](#d1)                                       | Estratégica  |
| D2  | [Transferencias internacionales: DPF + SCC como contingencia](#d2)                 | Operativa    |
| D3  | [Cifrado en reposo](#d3)                                                           | Operativa    |
| D4  | [Cifrado en tránsito](#d4)                                                         | Operativa    |
| D5  | [Categorización de datos en seis grupos](#d5)                                      | Estratégica  |
| D6  | [Borrado mixto: físico para PII, anonimización para datos derivados](#d6)          | Estratégica  |
| D7  | [Propagación del borrado vía evento `AlumnoEliminado`](#d7)                        | Operativa    |
| D8  | [Backups con retención acotada, no se restauran selectivamente](#d8)               | Operativa    |
| D9  | [Anonimización de IPs en logs operativos](#d9)                                     | Operativa    |
| D10 | [Política de retención por categoría](#d10)                                        | Estratégica  |
| D11 | [Plazo de atención a derechos: 1 mes (Art. 12.3)](#d11)                            | Operativa    |
| D12 | [Acceso y portabilidad: runbook manual + export JSON](#d12)                        | Operativa    |
| D13 | [Rectificación: vía aplicación (perfil del usuario)](#d13)                         | Operativa    |
| D14 | [Supresión: cruce al patrón de borrado mixto (D6)](#d14)                           | Operativa    |
| D15 | [Oposición y limitación: runbook manual](#d15)                                     | Operativa    |
| D16 | [Base legal de datos de salud: consentimiento explícito (Art. 9.2.a)](#d16)        | Estratégica  |
| D17 | [Tratamiento de menores: excluido del MVP con disparador](#d17)                    | Estratégica  |
| D18 | [Captura técnica del consentimiento: tabla `identidad.consentimiento`](#d18)       | Operativa    |
| D19 | [RAT (Art. 30): documento versionado en `docs/legal/rat.md`](#d19)                 | Operativa    |
| D20 | [DPIA simplificado documentado antes del lanzamiento](#d20)                        | Operativa    |
| D21 | [DPO no formal en MVP, con análisis documentado](#d21)                             | Estratégica  |
| D22 | [Subencargados: AWS + Postmark + GitHub con DPA firmados](#d22)                    | Operativa    |
| D23 | [Responsable del tratamiento: Runcriticon S.L.](#d23)                              | Estratégica  |
| D24 | [Notificación a AEPD ≤ 72 h (Art. 33)](#d24)                                       | Operativa    |
| D25 | [Comunicación a interesados si alto riesgo (Art. 34)](#d25)                        | Operativa    |
| D26 | [Runbook de respuesta a brechas](#d26)                                             | Operativa    |

## Contexto y problema

Runcriticon trata **datos personales y de salud sensibles** de personas reales en España. Seis ADR citan el RGPD como *driver*, pero **ninguna decisión de protección de datos estaba completa** — la auditoría de arquitectura lo identificó como el hueco de mayor prioridad. Hay que fijar las decisiones técnicas de protección de datos y dejarlas trazadas para que cada cruce desde otro ADR apunte a una sub-decisión concreta, no a "está en el 0014".

> **Alcance de este ADR.** Recoge las decisiones **técnicas y de arquitectura**. Las decisiones **jurídicas estrictas** —redacción de textos de información y consentimiento, firma final de los acuerdos de encargado, validación de la base legal— **requieren asesoría legal** y quedan recogidas en "Pendientes jurídicos", **no resueltas aquí**. Cuando un pendiente jurídico tiene una **postura técnica condicionante** (porque marca qué construir o cómo retener), esa postura sí se fija aquí — la asesoría legal ratifica o pide cambiar.

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR**. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Datos de salud sensibles** son el tipo de dato dominante (marcas, sesiones, lesiones, observaciones médicas) — categoría especial del Art. 9 RGPD.
- **Mono-tenant con `club_id` desde el día 1** (ADR-0006). El MVP es un club; la arquitectura está preparada para varios.
- **Spring Modulith + outbox + retención 30 días + reproyección** (ADR-0007 D6, D11, D15). Esto fija que los eventos en `event_publication` caducan en 30 días por compactación.
- **Hexagonal con `Result<T, DomainError>`** (ADR-0008 D11). Los errores RGPD (`SolicitudDuplicada`, `PlazoExcedido`, `ConsentimientoNoVigente`) son `DomainError`.
- **Modelo de identidad UUID v4 + `club_id` + rol único en MVP** (ADR-0003 D2).
- **Auditoría de identidad ya existe** con retención **12 meses** (ADR-0003 D15): tabla `identidad.evento_auditoria` con login, magic link, cambios de contraseña, recuperación por admin.
- **Auditoría de autorización ya existe** con módulo `auditoria` dedicado y anonimización al ejercer el olvido (ADR-0009 D15-D17).
- **Postmark como proveedor de email** con reglas RGPD ya fijadas sobre contenido (ADR-0005 D12): magic link y notificaciones de cambios sensibles permitidos; passwords en claro, datos de salud, tokens de sesión y datos de pago prohibidos.
- **Secretos en SSM Parameter Store como `SecureString`** (ADR-0013).
- **AWS `eu-west-1` (Irlanda) como región** (ADR-0006). Esta premisa formaliza una posición que ya vivía en el ADR-0014 original.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| **Plazo para atender un derecho RGPD (Art. 12.3)** | **1 mes**, prorrogable a 3 meses si complejo o numeroso, con comunicación al interesado |
| **Tiempo desde detección de brecha → notificación a AEPD (Art. 33)** | **≤ 72 h** |
| **Tiempo desde solicitud de supresión → propagación a todas las proyecciones** | **< 24 h p95** |
| **Tiempo desde supresión → backups con datos borrados (pasivamente, por caducidad)** | **≤ 30 días** |
| **Tiempo desde revocación de consentimiento → cesación del tratamiento dependiente** | **< 24 h p95** |
| **Cobertura del RAT** | **100 %** de los tratamientos activos al cierre de cada *sprint*; actualización en la misma PR que introduce un tratamiento nuevo |

## Drivers de la decisión

- Datos de salud sensibles + usuarios en España → el **RGPD aplica de lleno** y con categoría especial (Art. 9).
- **Responsabilidad proactiva**: poder demostrar el cumplimiento si la AEPD inspecciona.
- Equipo de 4 personas y MVP → soluciones **proporcionadas**, no sobrecargadas.
- Coherencia con la infraestructura (ADR-0006), la base de datos (ADR-0004), *events-first* (ADR-0007), el modelo de autorización y su auditoría (ADR-0009).
- **Cada sub-decisión debe estar cruzada** desde el ADR que la invoca; no más "está en el 0014" sin saber a qué punto.

## Opciones consideradas

- **Opción A** — Cumplimiento proporcionado al MVP: decisiones técnicas aquí; pendientes jurídicos delimitados y trazados.
- **Opción B** — Cumplimiento completo desde el día 1 con DPO formal, DPIA exhaustivo y auditoría externa.
- **Opción C** — Externalización total a un proveedor de *privacy compliance as a service*.

### Opción A — Cumplimiento proporcionado al MVP

Las decisiones técnicas y de arquitectura se cierran ahora; las jurídicas estrictas quedan trazadas como pendientes con responsable y plazo. La postura técnica condiciona el cumplimiento jurídico pero no lo sustituye.

- 👍 Proporcional al volumen (~550 usuarios, un club) y al equipo (4 personas).
- 👍 Coherente con el resto de ADRs ya aceptados (que ya asumen un patrón concreto).
- 👍 Permite lanzar la beta con cumplimiento sólido sin sobre-ingeniería.
- 👎 Asume que la asesoría legal cierra los pendientes antes del lanzamiento.

### Opción B — Cumplimiento completo desde el día 1

- 👍 Reduce a cero el riesgo de no cumplir un requisito.
- 👎 DPO formal y DPIA exhaustivo son desproporcionados para mono-club con 550 usuarios.
- 👎 Coste y tiempo que el MVP no puede absorber sin retrasar el piloto.

### Opción C — Externalización total

- 👍 Cero esfuerzo interno (en teoría).
- 👎 Coste recurrente alto.
- 👎 Lock-in en un proveedor para una capa que cruza toda la arquitectura.
- 👎 Los datos siguen siendo del producto; la externalización no quita responsabilidad. Mal trato coste-beneficio.

## Decisión

**Opción A: cumplimiento proporcionado al MVP.** Las veintiséis sub-decisiones desarrolladas a continuación. Siete son **estratégicas** (D1, D5, D6, D10, D16, D17, D21, D23 — residencia, categorización de datos, borrado mixto, retención, base legal, menores, posición sobre DPO, responsable del tratamiento); el resto son **operativas** y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — Residencia: AWS `eu-west-1` (Irlanda)

Todo el tratamiento de datos se hace en la **UE**: región AWS `eu-west-1`. Cubre la residencia UE/EEE que el RGPD exige, tiene App Runner (ADR-0006) y es la región más madura. Se descartó `eu-south-2` (España) por el riesgo de que App Runner no esté disponible — y el RGPD **no exige España**, basta la UE/EEE.

La región se fija en la IaC (Terraform, ADR-0006); revisión arquitectónica de que ningún recurso con datos se crea fuera de la UE.

<a id="d2"></a>
### D2 — Transferencias internacionales: DPF + SCC como contingencia

Hay subencargados fuera de la UE (Postmark en EE.UU., GitHub en EE.UU.). Cada uno requiere un **mecanismo de transferencia internacional** válido:

- **Data Privacy Framework (DPF) UE-EE.UU.** cuando el subencargado está adherido.
- **Cláusulas Contractuales Tipo (SCC)** como **contingencia**: se firman también, no solo se confía en el DPF. Razón histórica: el régimen de transferencias UE-EE.UU. ha caído dos veces (Safe Harbor 2015, Privacy Shield 2020). Si el DPF cae, las SCC siguen cubriendo.

Pendiente jurídico: firma de los DPA y verificación del mecanismo elegido por cada subencargado.

<a id="d3"></a>
### D3 — Cifrado en reposo

- **Base de datos (RDS PostgreSQL)**: cifrado activado, cubre también los *snapshots* (backups).
- **Secretos**: SSM Parameter Store con `SecureString` (ADR-0013), cifrados con KMS.
- **Logs en CloudWatch / S3**: cifrados con KMS por defecto.
- **Artefactos en GHCR**: las imágenes Docker no contienen PII por construcción (pipeline de ADR-0010); el cifrado en reposo lo provee GHCR.

<a id="d4"></a>
### D4 — Cifrado en tránsito

- **HTTPS obligatorio** para todo el tráfico HTTP (cookie de sesión `Secure`, ADR-0003 D10).
- **Conexión cifrada** entre App Runner y RDS (TLS).
- **Conexión cifrada** entre el adaptador de email y Postmark (TLS).
- **HTTP plano rechazado** en el balanceador / front-door.

<a id="d5"></a>
### D5 — Categorización de datos en seis grupos

Toda tabla/almacén del producto pertenece a una de **seis categorías**. La categoría determina su política de retención (D10) y su tratamiento al ejercer el derecho al olvido (D6).

| Cat. | Contenido | Ejemplo |
|------|-----------|---------|
| **1 — PII primaria** | Datos personales identificables que constituyen la cuenta y sus datos de salud | `identidad.usuario`, `seguimiento.alumno_perfil`, `seguimiento.reporte_sesion`, `seguimiento.marca` |
| **2 — Auditoría local de módulo** | Eventos de auditoría propios de un módulo (altas, bajas, cambios de estado) para investigar incidentes y rendir cuentas — **no** cruza al bounded context `auditoria` (cat. 3), que es exclusivamente de autorización | `identidad.evento_auditoria` (ADR-0003 D15), `club_taxonomia.evento_auditoria` (LAL-87) |
| **3 — Auditoría de autorización** | Denegaciones de autorización y accesos a datos sensibles | `auditoria.evento` (ADR-0009 D17) |
| **4 — Outbox** | Eventos publicados pendientes o procesados, con payload completo | `event_publication` de Spring Modulith (ADR-0007 D6) |
| **5 — Backups** | Snapshots completos de la base de datos | RDS snapshots, copias de seguridad |
| **6 — Logs operativos** | Acceso HTTP, logs de aplicación, métricas | CloudWatch Logs, traza estructurada |

Cada PR que introduce una tabla nueva debe explicitar a qué categoría pertenece. ArchUnit o convención de revisión vigila que no aparezcan tablas con PII fuera del módulo Identidad/Seguimiento sin pasar por revisión RGPD.

**Corrección expresa (2026-08-23, LAL-87):** la categoría 2 se redactó originalmente como "Auditoría de identidad", pensada para el único módulo que la necesitaba entonces. Un segundo módulo (`club_taxonomia`, con su propio historial local de cambios de tags) confirma que el patrón — una tabla de auditoría *local* a un módulo, distinta del bounded context `auditoria` — es genérico, no exclusivo de `identidad`. Se generaliza la definición sin tocar el conteo de seis categorías ni el nombre `AUDITORIA_IDENTIDAD` del enum `Category` (evita renombrar código ya mergeado sin necesidad).

<a id="d6"></a>
### D6 — Borrado mixto: físico para PII, anonimización para datos derivados

Al ejercer el derecho de supresión, el tratamiento varía por categoría (D5):

| Cat. | Acción al ejercer olvido |
|------|--------------------------|
| **1 — PII primaria** | **Borrado físico** de filas. Propagación a proyecciones locales por evento (D7). |
| **2 — Auditoría local de módulo** | **Anonimización**: `actor_id` y `sujeto_id` → `NULL`, IP truncada a /24, `metadata` purgado de campos con PII. La fila se mantiene por responsabilidad proactiva. Aplica igual en cualquier módulo con tabla de esta categoría. |
| **3 — Auditoría de autorización** | Anonimización igual que cat. 2. |
| **4 — Outbox** | **Pasiva**: el outbox compacta tras 30 días (ADR-0007 D15); en el ínterin, los eventos pendientes se procesan y los ya procesados se compactan. Si el evento aún contiene PII del usuario borrado y aún no ha caducado, se anonimiza durante la compactación. |
| **5 — Backups** | **Pasiva**: los backups con la PII desaparecen al caducar (≤ 30 días). **No se restauran selectivamente** para resucitar datos borrados — admisible bajo el RGPD si la retención está limitada y la política se respeta. |
| **6 — Logs operativos** | Las IPs ya están truncadas (D9). El `userId` se sustituye en logs por un hash determinístico (HMAC-SHA256 con salt) que permite agrupar sin reidentificar; al ejercer olvido el hash sigue siendo opaco. |

**Corrección expresa del ADR-0014 original**: se sustituye *"se descartó la anonimización"* por este patrón mixto. Razón: la anonimización en datos derivados es necesaria para que la responsabilidad proactiva (Arts. 5.2, 24) no caiga al borrar PII primaria.

Esta política se documenta en el RAT (D19) y se aplica por convenciones cubiertas por tests de integración (purga del usuario `eliminado@test` deja la auditoría con `actor_id NULL` y la PII primaria sin trazas).

<a id="d7"></a>
### D7 — Propagación del borrado vía evento `AlumnoEliminado`

Al ejercer el derecho de supresión, el módulo Identidad emite el evento `AlumnoEliminado(usuarioId, clubId, occurredAt)`. Cada módulo que tiene proyección local de este usuario:

- Borra físicamente sus filas en la categoría 1 (PII).
- Anonimiza sus filas en las categorías 2-3 según D6.
- Acuse de procesamiento idempotente (ADR-0007 D9): el evento puede llegar más de una vez sin efectos colaterales.

Política de fallos (ADR-0007 D13) aplica: si un módulo no consume el evento tras 5 reintentos, queda en DLQ + alarma. El plazo NFR de < 24h p95 (Bloque NFRs) marca el techo.

<a id="d8"></a>
### D8 — Backups con retención acotada, no se restauran selectivamente

- **Retención de backups**: 30 días (RDS automated snapshots).
- **No se restauran backups parciales** para resucitar PII borrada por un derecho de supresión. Si una restauración completa es necesaria (recuperación de desastre), inmediatamente tras restaurar se vuelve a aplicar la lista de usuarios borrados pendientes — registro auditado de qué olvidos se reaplican.
- El runbook de recuperación de desastre incluye este paso explícitamente; el equipo no puede saltarlo.

<a id="d9"></a>
### D9 — Anonimización de IPs en logs operativos

- **Logs operativos** (acceso HTTP, aplicación): IP truncada a **/24 IPv4** (último octeto → 0) o **/48 IPv6**. Suficiente para forense por región/AS, ya no es PII directa.
- **Logs de auditoría** (categorías 2 y 3, D5): IP completa. Justificación: son logs de seguridad, no operativos, y se anonimizan al ejercer el olvido (D6).
- **`userId` en logs operativos**: hash determinístico con salt rotado anualmente. Permite agrupar peticiones del mismo usuario sin reidentificar.

<a id="d10"></a>
### D10 — Política de retención por categoría

| Categoría (D5) | Retención por defecto | Disparador de purga |
|---|---|---|
| 1 — PII primaria | Hasta baja + **30 días** de gracia | Marcar baja + cron diario que purga las cuentas en gracia caducada |
| 2 — Auditoría de identidad | **12 meses** | Cron mensual purga filas con `ts < now() - 12 months` |
| 3 — Auditoría de autorización | **24 meses** | Cron mensual purga filas con `ts < now() - 24 months` |
| 4 — Outbox | **30 días** (compactación ADR-0007 D15) | Job interno de Spring Modulith |
| 5 — Backups | **30 días** | Retención automática RDS |
| 6 — Logs operativos | **90 días** | Política de retención CloudWatch |

Si entra una exigencia regulatoria distinta, se reabre este ADR (o se ajusta D10 con nueva revisión).

<a id="d11"></a>
### D11 — Plazo de atención a derechos: 1 mes (Art. 12.3)

El responsable atiende los derechos RGPD en **1 mes** desde la solicitud, prorrogable a **3 meses** si la solicitud es compleja o numerosa, con **comunicación al interesado** dentro del primer mes explicando la prórroga y su razón.

Implementación: el correo del responsable (`privacidad@runcriticon.com`) está documentado en la política de privacidad y en el aviso legal del producto. Las solicitudes se atienden con los runbooks de D12/D15.

<a id="d12"></a>
### D12 — Acceso y portabilidad: runbook manual + export JSON

- **Atención**: runbook documentado en `docs/runbooks/derechos-rgpd-acceso.md`. El admin o el responsable ejecuta el procedimiento; no es self-service.
- **Formato de salida**: JSON estructurado con esquema documentado, suficiente para la portabilidad (Art. 20). El JSON cubre toda la PII primaria (categoría 1, D5) del usuario y, si aplica, su auditoría asociada anonimizada de terceros.
- **Verificación de identidad**: el responsable verifica que la solicitud proviene del titular (no de un suplantador) antes de generar el export. Métodos aceptados: confirmación por email vigente + confirmación fuera de banda con el admin del club.
- **Sin acceso descontrolado a producción**: el runbook usa consulta acotada por `usuarioId`, no exporta nada por club o transversal.

**Disparadores que reabren la decisión** (obligan a construir export self-service):

- Entra el **segundo club** en la plataforma — la atención manual escala mal con N clubes en paralelo.
- Se atienden más de **5 solicitudes de acceso o portabilidad por mes** durante **dos meses consecutivos** — pierde rentabilidad el procedimiento manual.

Por debajo de esos umbrales, el runbook manual es proporcional al volumen. El modelo relacional acotado por usuario hace barata la futura implementación cuando llegue el momento.

<a id="d13"></a>
### D13 — Rectificación: vía aplicación (perfil del usuario)

El usuario rectifica sus datos personales directamente desde su perfil (nombre, email — con confirmación de ADR-0003 D9, teléfono, dirección postal). No requiere intervención del admin.

Los datos que el usuario **no puede modificar** por sí mismo (rol, asignación a grupo, marcas históricas, reportes que él no creó) se rectifican vía solicitud al admin del club + ejecución por el responsable según runbook.

<a id="d14"></a>
### D14 — Supresión: cruce al patrón de borrado mixto (D6)

El derecho de supresión se materializa por el patrón de D6 + propagación de D7 + caducidad pasiva de D8. Plazo: 1 mes (D11) con propagación interna < 24 h p95 (NFRs).

El usuario puede pedir la supresión:
- Desde su perfil (botón "Eliminar mi cuenta", confirmación fuerte).
- Por solicitud al responsable (correo `privacidad@runcriticon.com`).

En ambos casos, el flujo: confirmación → marcar baja → emitir `AlumnoEliminado` → propagación → confirmación de cierre al titular.

<a id="d15"></a>
### D15 — Oposición y limitación: runbook manual

- **Oposición** (Art. 21): runbook documentado en `docs/runbooks/derechos-rgpd-oposicion.md`. En la práctica, la oposición al tratamiento implica revocar el consentimiento (D18) y/o solicitar la supresión (D14).
- **Limitación** (Art. 18): runbook similar; supone marcar el usuario como `LIMITADO` y bloquear cualquier tratamiento de sus datos salvo conservación. Implementación: estado nuevo en `identidad.usuario`; el módulo Seguimiento rechaza operaciones con `Result.LimitacionVigente`.

<a id="d16"></a>
### D16 — Base legal de datos de salud: consentimiento explícito (Art. 9.2.a)

**Base legal de tratamiento de datos de salud**: **consentimiento explícito del interesado**, Art. 9.2.a del RGPD.

Justificación técnica:
- Es la base legal más sencilla de implementar y demostrar para un club amateur.
- No requiere acreditar actividad sanitaria formal (Art. 9.2.h) que el club piloto no tiene.
- Implica construir: pantalla de consentimiento al activar la cuenta (sobre texto versionado) + tabla `identidad.consentimiento` (D18) + posibilidad de revocar.

Pendiente jurídico: redacción de los **textos de información y consentimiento** por asesoría legal antes del lanzamiento.

<a id="d17"></a>
### D17 — Tratamiento de menores: excluido del MVP con disparador

El MVP **no trata datos de menores**. El club piloto declara que solo gestiona usuarios mayores de edad; esta declaración se documenta en el alta del club piloto.

Disparadores que reabren la decisión (obligan a construir gestión de consentimiento parental):

- Entra un club con usuarios menores de edad.
- Aparece la primera solicitud de alta de un menor en el club piloto contrariando la declaración.

Mientras tanto, queda **prohibido** dar de alta a menores en el club piloto. El sistema **no construye** todavía: captura de fecha de nacimiento, discriminación < 14 años (LOPDGDD Art. 7), captura de consentimiento del tutor.

<a id="d18"></a>
### D18 — Captura técnica del consentimiento: tabla `identidad.consentimiento`

Para sostener la base legal de D16, se persiste el consentimiento de forma demostrable.

Tabla `identidad.consentimiento` con: `usuario_id`, `version_texto`, `concedido_en`, `revocado_en NULL`, `ip` (completa, por seguridad), `user_agent`.

Flujo:
- **Concesión**: al activar la cuenta (ADR-0003 D4) sobre la versión vigente del texto, con casilla **no premarcada** y acción afirmativa (click). El consentimiento queda persistido.
- **Revocación**: botón en el perfil del usuario. Tras revocar, el módulo Seguimiento rechaza nuevas operaciones de tratamiento (`Result.ConsentimientoNoVigente`) y se notifica al usuario que su revocación implica solicitar la supresión o la limitación si quiere mantener la cuenta.
- **Versiones del texto**: numeradas y conservadas en el repo (`docs/legal/consentimiento/`). Cambios sustanciales requieren reconfirmación del consentimiento por todos los usuarios activos.

Pendiente jurídico: criterios para distinguir "cambio sustancial" que dispara reconfirmación.

<a id="d19"></a>
### D19 — RAT (Art. 30): documento versionado en `docs/legal/rat.md`

El **Registro de Actividades de Tratamiento** (RAT, Art. 30 RGPD) es **obligatorio** porque se tratan datos de salud (la excepción de < 250 personas no aplica).

- Vive como `docs/legal/rat.md`, versionado en el repo.
- Cada PR que introduce o modifica un tratamiento debe actualizarlo en el mismo commit. Un **pre-commit hook** señala las PRs que tocan migraciones SQL sin tocar el RAT (no es bloqueante: hay cambios técnicos que no afectan al RAT; el hook fuerza a justificarlo en la PR).
- Contiene: nombre y datos del responsable, finalidades del tratamiento, categorías de interesados y datos, destinatarios, transferencias internacionales (D2), plazos de supresión (D10), medidas técnicas y organizativas.

<a id="d20"></a>
### D20 — DPIA simplificado documentado antes del lanzamiento

El **Art. 35** exige una Evaluación de Impacto en la Protección de Datos (DPIA) cuando se traten datos de salud "a gran escala". 550 usuarios no es claramente "gran escala", pero **el principio de cautela** aconseja documentar una DPIA simplificada antes del lanzamiento.

Contenido mínimo (`docs/legal/dpia.md`):
- Descripción del tratamiento.
- Necesidad y proporcionalidad.
- Riesgos para los derechos y libertades (reidentificación, brecha, uso indebido por entrenadores).
- Medidas mitigadoras (este ADR completo).
- Conclusión: riesgo residual aceptable / no aceptable.

Pendiente jurídico: validación del DPIA antes del lanzamiento.

<a id="d21"></a>
### D21 — DPO no formal en MVP, con análisis documentado

**No se designa Delegado de Protección de Datos formal** en MVP.

Análisis (`docs/legal/dpo-analisis.md`):
- Volumen: mono-club, ~550 usuarios → no es "gran escala" para Art. 37.1.b.
- No es autoridad pública (Art. 37.1.a no aplica).
- La actividad principal no es la "observación sistemática a gran escala" (Art. 37.1.b).

La responsabilidad de protección de datos recae en el responsable del tratamiento (D23) y la función de contacto (`privacidad@runcriticon.com`) está documentada.

Disparadores que reabren la decisión (obligan a DPO formal):
- Más de 1 000 usuarios totales en la plataforma.
- Entra cualquier indicio de "observación sistemática a gran escala".
- Recomendación de la asesoría legal.

<a id="d22"></a>
### D22 — Subencargados: AWS + Postmark + GitHub con DPA firmados

Lista nominal de subencargados del tratamiento:

| Subencargado | Función | DPA | Transferencia internacional |
|---|---|---|---|
| **AWS** | Alojamiento (App Runner), base de datos (RDS), backups, logs, secretos | "AWS GDPR DPA" | Tratamiento en `eu-west-1` (D1); el subencargado es internacional pero los datos no salen de la UE |
| **Postmark** | Envío de email transaccional (ADR-0005) | DPA Postmark | DPF UE-EE.UU. + SCC como contingencia (D2) |
| **GitHub** | CI/CD (Actions), registro de imágenes (GHCR), código fuente | DPA según plan contratado | DPF UE-EE.UU. + SCC como contingencia (D2) |

Cualquier nuevo subencargado pasa por revisión RGPD antes de incorporarse y se añade a esta lista + RAT (D19).

Pendiente jurídico: firma efectiva de cada DPA con su contraparte.

<a id="d23"></a>
### D23 — Responsable del tratamiento: Runcriticon S.L.

El **responsable del tratamiento** a efectos del RGPD es la entidad jurídica que opera el producto: **Runcriticon S.L.** (denominación a confirmar por asesoría legal).

- El responsable decide los fines y medios del tratamiento.
- Los clubes son **usuarios** del producto; respecto de los datos de sus miembros que tratan dentro de la plataforma, son **responsables de su uso interno** (categorización jurídica final por asesoría legal: corresponsable Art. 26 o encargado Art. 28).
- AWS, Postmark y GitHub son **encargados/subencargados** (D22).
- El contacto del responsable es `privacidad@runcriticon.com`, documentado en la política de privacidad y el aviso legal.

Pendiente jurídico: constitución formal de la entidad y formalización de la relación contractual con cada club.

<a id="d24"></a>
### D24 — Notificación a AEPD ≤ 72 h (Art. 33)

Toda brecha de seguridad que pueda suponer un riesgo para los derechos y libertades de las personas se notifica a la **Agencia Española de Protección de Datos (AEPD)** en **≤ 72 horas** desde su detección.

- El cómputo comienza en el momento en que el responsable es **consciente** de la brecha, no en el momento del ataque.
- Si la notificación llega más tarde de 72 h, debe explicarse el motivo de la dilación.
- El runbook (D26) tiene la plantilla de notificación y el flujo paso a paso.

<a id="d25"></a>
### D25 — Comunicación a interesados si alto riesgo (Art. 34)

Si la brecha entraña un **alto riesgo** para los derechos y libertades de los afectados, se les comunica directamente, **sin dilación indebida**, en lenguaje claro y sencillo.

- Excepciones (Art. 34.3): los datos estaban cifrados de forma tal que la brecha no compromete su confidencialidad, o el responsable ha tomado medidas posteriores que impiden el alto riesgo.
- Canal de comunicación: email transaccional (ADR-0005) al email vigente del afectado + publicación en una página `/avisos-de-seguridad` del producto.

<a id="d26"></a>
### D26 — Runbook de respuesta a brechas

Vive como `docs/runbooks/respuesta-a-brecha.md`. Pasos mínimos:

1. **Detección** — alarmas de ADR-0010 D22 + denuncia interna o externa.
2. **Contención** — revocar credenciales comprometidas, aislar entornos, cortar el vector.
3. **Análisis del alcance** — qué datos, cuántos usuarios, qué riesgo para los derechos.
4. **Decisión de notificación** — D24 (AEPD) siempre que aplique; D25 (interesados) si alto riesgo.
5. **Notificación a la AEPD** — plantilla incluida en el runbook (datos del responsable, naturaleza de la brecha, categorías de datos y de personas afectadas, posibles consecuencias, medidas adoptadas).
6. **Comunicación a afectados** — si aplica D25.
7. **Postmortem** — cruce a ADR-0010 D23: postmortem obligatorio en plazo de 1 semana.

## Pendientes jurídicos (no resueltos en este ADR)

Requieren **asesoría legal** antes del lanzamiento con el club piloto:

- **Validación de la base legal del tratamiento** (D16) y, en su caso, ratificación de la opción Art. 9.2.a.
- **Redacción de los textos de información y consentimiento** (D18) y de la política de privacidad y el aviso legal del producto.
- **Firma efectiva de los DPA** con AWS, Postmark y GitHub (D22).
- **Constitución formal de Runcriticon S.L.** y formalización de la relación contractual con cada club (D23).
- **Validación del DPIA simplificado** (D20).
- **Validación del análisis "sin DPO formal"** (D21).
- **Criterios para distinguir "cambio sustancial"** del texto del consentimiento que dispara reconfirmación (D18).
- **Política de retención** validada con criterio legal (D10).
- **Categorización jurídica de la relación con cada club** (corresponsable Art. 26 vs encargado Art. 28, D23).

## Consecuencias

### Positivas

- Las decisiones de protección de datos dejan de estar implícitas — quedan registradas, numeradas, trazables.
- Patrón de borrado mixto (D6) **coherente con el resto de la arquitectura** (ADR-0009 D17) y con la realidad: PII borrada físicamente, auditoría conservada anonimizada.
- Categorización en seis grupos (D5) hace operable la decisión: cada tabla nueva sabe a qué categoría pertenece y qué política le aplica.
- Política de retención (D10) explícita y por categoría — fin de las ambigüedades.
- Base legal de salud (D16) y captura técnica del consentimiento (D18) cierran el flujo de Art. 9.
- Lista nominal de subencargados (D22) y responsable explícito (D23) cierran Art. 28 y Art. 24.
- Notificación de brechas (D24-D26) operacionalizada con runbook + plantilla, no como buena intención.
- Anonimización de IPs (D9) reduce la huella de PII en logs operativos sin perder forense útil.
- Cada cruce desde otros ADRs apunta a una sub-decisión concreta del 0014.
- Coste y esfuerzo proporcionados al MVP (Opción A).

### Negativas / coste asumido

- El borrado mixto exige disciplina: cada tabla nueva debe categorizarse correctamente; un error mete PII donde debería estar anonimizado.
- Pendientes jurídicos no se cierran solos; requieren asesoría legal antes del lanzamiento.
- DPIA simplificado y análisis sin DPO formal hay que escribirlos, no son automáticos.
- El RAT (D19) requiere mantenimiento con cada cambio de tratamiento.
- Acceso y portabilidad manuales (D12) — trabajo por solicitud; asumible por volumen esperado.

### Riesgos y mitigaciones

- **Lanzar con los pendientes jurídicos sin cerrar** → no lanzar con el club piloto hasta resolverlos con asesoría legal.
- **Borrado incompleto entre módulos** → evento `AlumnoEliminado` (D7) con política de fallos del outbox + tests que verifiquen purga en cada proyección local.
- **Datos en región equivocada** → región fijada en IaC (Terraform, ADR-0006); revisión arquitectónica.
- **Caída del DPF UE-EE.UU.** → SCC firmadas también (D2) como contingencia.
- **Restauración accidental de PII borrada desde backup** → runbook de recuperación de desastre (D8) incluye la reaplicación obligatoria de la lista de olvidos.
- **PII filtrada a logs operativos** → IP truncada (D9), `userId` hasheado, convención de no loguear payloads completos. Revisión arquitectónica en cada PR que introduce logs.
- **Tabla nueva categorizada mal** → ArchUnit o convención de revisión exige declaración explícita de categoría (D5) en cada PR de migración.
- **Solicitud de alta de menor** (D17) → declaración firmada del club piloto + revisión humana de las altas masivas iniciales.

## Notas

- Las premisas heredadas (especialmente ADR-0003 D2/D15, ADR-0007 D11/D15, ADR-0009 D15-D17) son **invariantes de este ADR**: si cambian, este ADR se revisita.
- Las decisiones jurídicas estrictas se ratifican en revisión por asesoría legal; este ADR fija solo la **postura técnica** que condiciona qué construir y cómo retener.
- La **funcionalidad de exportación self-service** (D12) es mejora posterior, no del MVP.
- **Cookies**: ADR-0003 D14 ya cerró que la cookie de sesión es técnica esencial y no requiere banner. Si entran cookies no esenciales (analítica de terceros), este ADR se reabre.
- **Revisión periódica**: este ADR se revisa al **lanzamiento del piloto** (cierre de pendientes jurídicos) y luego anualmente, o cuando alguno de los disparadores específicos se active (D17 menores, D21 escala, etc.).
- **Reorganización del 2026-05-29 (Nivel 1)**: el ADR se reestructura con índice de sub-decisiones (párrafo introductorio + tabla), premisas heredadas, NFRs explícitos, numeración D1-D26 con anchors. Corrección expresa: "anonimización descartada" → patrón de borrado mixto (D6), restablece coherencia con ADR-0009 D17. Decisiones nuevas explicitadas: categorización (D5), retención por categoría (D10), base legal salud (D16), menores (D17), captura del consentimiento (D18), RAT (D19), DPIA (D20), DPO (D21), subencargados nominales (D22), responsable (D23), runbook brechas (D26), anonimización de IPs (D9).
