# Backlog temporal de mejoras

## 1. Uso y prioridad

Este backlog sirve para corregir la documentación y preparar el MVP. No es todavía la fuente normativa definitiva; cada elemento aprobado debe trasladarse a los documentos de `docs` que correspondan.

| Prioridad | Significado |
|---|---|
| P0 | Bloquea una implementación coherente o una beta con datos reales. |
| P1 | Imprescindible para que el MVP aporte el valor decidido. |
| P2 | Evolución posterior al MVP. |
| Fuera de alcance | Capacidad que no debe contaminar esta versión. |

Todos los responsables y fechas permanecen como `Pendiente de asignar` hasta que una persona los acepte expresamente.

## 2. Orden recomendado

1. Cerrar P0 de documentación y privacidad.
2. Alinear cuentas, roles, tags y pertenencia a grupos.
3. Construir el plan semanal estructurado y los ritmos relativos.
4. Cerrar el ciclo de reportes, alertas, comentarios y notificaciones.
5. Incorporar dashboard e histórico.
6. Ejecutar beta solo cuando estén satisfechos los gates jurídicos y operativos.

---

## P0 — Coherencia documental

### DOC-P0-01 — Declarar el posicionamiento y las exclusiones

- **Prioridad:** P0.
- **Problema:** `docs` presenta el producto como gestión de clubes aunque el MVP no gestiona la relación comercial o societaria.
- **Evidencia:** [`auditoria-documental.md`](auditoria-documental.md#2-evidencia-de-negocio-y-posicionamiento), [`../docs/vision.md`](../docs/vision.md) y [`../docs/backlog.md`](../docs/backlog.md).
- **Cambio propuesto:** definir el MVP como gestor de entrenamientos para un club de running e incorporar las exclusiones de [`README.md`](README.md#fuera-del-mvp).
- **Criterios de aceptación:**
  - `vision.md`, `backlog.md` y el plan de implementación usan el mismo posicionamiento.
  - Pagos, membresías, contabilidad, eventos y comunicación general aparecen como exclusiones explícitas.
  - Alta de usuarios, grupos y permisos permanecen dentro por ser dependencias operativas del entrenamiento.
- **Dependencias:** ninguna.
- **Riesgo:** volver a prometer un SaaS integral que el MVP no entrega.
- **Estado:** Decidido; pendiente de trasladar a `docs`.
- **Responsable:** Pendiente de asignar.

### DOC-P0-02 — Crear una única matriz normativa del MVP

- **Prioridad:** P0.
- **Problema:** backlog, wireframes, ADR y plan discrepan sobre Google, altas masivas, ritmos relativos, roles y grupos.
- **Evidencia:** [`auditoria-documental.md`](auditoria-documental.md#3-matriz-de-hallazgos-y-resolución).
- **Cambio propuesto:** crear en `docs` una matriz `requisito → decisión → criterio → journey → pantalla → permiso → ADR → fase` y declarar el documento autoritativo por ámbito.
- **Criterios de aceptación:**
  - Cada MUST tiene un único estado de alcance.
  - Ningún wireframe o prototipo introduce reglas no presentes en un requisito o decisión enlazada.
  - Los artefactos sustituidos incluyen `superseded_by` o están archivados inequívocamente.
  - Los conteos de MUST se calculan o validan automáticamente.
- **Dependencias:** DOC-P0-01 y aprobación de este backlog.
- **Riesgo:** estimaciones imposibles y desarrollo de decisiones incompatibles.
- **Estado:** Decidido; pendiente de ejecución.
- **Responsable:** Pendiente de asignar.

### DOC-P0-03 — Corregir terminología, enlaces y gobierno ADR

- **Prioridad:** P0.
- **Problema:** se mezclan Alumno, Atleta y Corredor; `estado` representa conceptos distintos; existen enlaces rotos y cambios materiales dentro de ADR aceptados.
- **Evidencia:** [`auditoria-documental.md`](auditoria-documental.md#3-matriz-de-hallazgos-y-resolución) y [`../docs/adr/README.md`](../docs/adr/README.md).
- **Cambio propuesto:** adoptar `Alumno`, diferenciar estado de cuenta, disponibilidad deportiva y resultado de sesión, validar enlaces en CI y exigir ADR sucesor para cambios normativos.
- **Criterios de aceptación:**
  - `Alumno` es el término funcional canónico.
  - Cada estado tiene nombre no ambiguo y valores documentados.
  - El validador de enlaces internos no reporta destinos inexistentes, salvo referencias futuras marcadas expresamente.
  - Una modificación de decisión aceptada crea un ADR nuevo que sustituye al anterior.
- **Dependencias:** DOC-P0-02.
- **Riesgo:** contratos, interfaz y pruebas interpretan de forma distinta el mismo concepto.
- **Estado:** Decidido; pendiente de ejecución.
- **Responsable:** Pendiente de asignar.

### DOC-P0-04 — Actualizar el estado real de implementación

- **Prioridad:** P0.
- **Problema:** el plan declara un proyecto greenfield sin código y contiene fases y exclusiones desactualizadas.
- **Evidencia:** [`../docs/plan-implementacion-mvp.md`](../docs/plan-implementacion-mvp.md).
- **Cambio propuesto:** sustituir la fotografía inicial por un estado fechado y trazable al backlog vigente.
- **Criterios de aceptación:**
  - El plan indica fecha de corte, funcionalidades terminadas, parciales y no iniciadas.
  - No usa `greenfield` para describir el estado actual.
  - Cada fase enlaza requisitos todavía vigentes.
- **Dependencias:** DOC-P0-02.
- **Riesgo:** secuenciación y estimación basadas en una situación inexistente.
- **Estado:** Pendiente.
- **Responsable:** Pendiente de asignar.

---

## P0 — Preparación jurídica y operativa de beta

### BET-P0-01 — Completar preparación de privacidad

- **Prioridad:** P0; bloquea beta con datos reales.
- **Problema:** faltan RAT, decisiones jurídicas, retención aplicable y evidencia sobre tratamiento de molestias, comentarios, marcas e historial.
- **Evidencia:** [`../docs/adr/0014-proteccion-de-datos-rgpd.md`](../docs/adr/0014-proteccion-de-datos-rgpd.md), [`../docs/glosario.md`](../docs/glosario.md) y [`decisiones-pendientes.md`](decisiones-pendientes.md).
- **Cambio propuesto:** completar los artefactos de privacidad exigidos por la documentación y validar específicamente el modelo de eliminación con historial y reutilización de email.
- **Criterios de aceptación:**
  - Existe RAT versionado y revisado.
  - Base jurídica, roles responsable/encargado, retención, borrado y anonimización tienen decisión y evidencia.
  - Textos de privacidad explican quién ve marcas, reportes, comentarios y disponibilidad.
  - La eliminación de una cuenta con actividad conserva o anonimiza cada dato según una regla aprobada.
  - Privacidad/DPO registra revisión humana; este criterio no presupone aprobación favorable.
- **Dependencias:** revisión jurídica externa al equipo de desarrollo.
- **Riesgo:** tratamiento no autorizado de datos personales o pérdida indebida de trazabilidad.
- **Estado:** Bloqueado.
- **Responsable:** Pendiente de asignar; revisión requerida de Privacidad/DPO.

### BET-P0-02 — Completar runbooks obligatorios

- **Prioridad:** P0; bloquea beta.
- **Problema:** eliminación solicitada por el Alumno, eliminación de Administradores, brechas, recuperación y derechos de acceso dependen de procedimientos inexistentes o incompletos.
- **Evidencia:** [`../docs/runbooks/README.md`](../docs/runbooks/README.md) y [`decisiones-pendientes.md`](decisiones-pendientes.md).
- **Cambio propuesto:** redactar, ensayar y asignar runbooks de privacidad y continuidad.
- **Criterios de aceptación:**
  - Existen procedimientos de eliminación, acceso/oposición, brecha, recuperación y cuenta administradora.
  - Cada runbook tiene disparador, responsable, pasos, evidencias, escalado y criterio de cierre.
  - Se registra al menos un ensayo antes de beta.
- **Dependencias:** BET-P0-01.
- **Riesgo:** incapacidad de responder a incidentes o solicitudes dentro de plazo.
- **Estado:** Bloqueado.
- **Responsable:** Pendiente de asignar.

### BET-P0-03 — Definir éxito y salida de la beta

- **Prioridad:** P0 antes de iniciar beta.
- **Problema:** los objetivos a 12 meses y criterios go/no-go siguen vacíos.
- **Evidencia:** [`../docs/vision.md`](../docs/vision.md) y [`decisiones-pendientes.md`](decisiones-pendientes.md).
- **Cambio propuesto:** fijar métricas de activación, publicación, reporte, uso del ritmo relativo, tiempo de planificación y retención del club piloto.
- **Criterios de aceptación:**
  - Cada métrica tiene fórmula, fuente, ventana, objetivo y responsable.
  - Existe fecha de revisión y regla explícita para continuar, corregir o detener.
  - Pricing y gestión integral del club no se introducen de forma encubierta en el MVP.
- **Dependencias:** DOC-P0-01.
- **Riesgo:** beta sin capacidad de demostrar o refutar valor.
- **Estado:** Requiere decisión de negocio.
- **Responsable:** Pendiente de asignar.

---

## P1 — Acceso y ciclo de cuentas

### ACC-P1-01 — Autenticación por email

- **Prioridad:** P1.
- **Problema:** el alcance de Google y los métodos de acceso era contradictorio.
- **Evidencia:** decisión consolidada en [`auditoria-documental.md`](auditoria-documental.md#41-roles-y-cuentas).
- **Cambio propuesto:** permitir a cualquier usuario activo entrar por contraseña o enlace mágico; excluir Google.
- **Criterios de aceptación:**
  - Una cuenta activa puede usar indistintamente contraseña o solicitar enlace mágico.
  - Invitaciones, enlaces mágicos, recuperación y avisos críticos se envían aunque el usuario desactive otros emails.
  - Un usuario no invitado no puede crear cuenta públicamente.
  - Los errores no revelan si un email existe.
- **Dependencias:** modelo actual de identidad.
- **Riesgo:** confusión de cuentas o enumeración de usuarios.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### ACC-P1-02 — Alta masiva pegando emails

- **Prioridad:** P1.
- **Problema:** el alta individual no escala y el CSV es demasiado complejo para el MVP.
- **Evidencia:** [`../docs/journeys/admin-setup.md`](../docs/journeys/admin-setup.md) y decisión consolidada.
- **Cambio propuesto:** permitir pegar emails, revisar el resultado y enviar invitaciones en bloque.
- **Criterios de aceptación:**
  - El Administrador pega uno o varios emails separados por saltos de línea o copiados desde una hoja de cálculo.
  - Los duplicados de la entrada se consolidan.
  - Una cuenta activa se reporta como conflicto y no se modifica.
  - Una invitación pendiente no se duplica y puede reenviarse.
  - El Administrador revisa válidos, conflictos y errores antes de enviar.
  - El grupo es opcional; si se indica, la incorporación efectiva es un lunes.
  - La invitación se envía inmediatamente, caduca en siete días y un reenvío invalida enlaces anteriores.
- **Dependencias:** ACC-P1-01 y ORG-P1-03.
- **Riesgo:** altas duplicadas o abandono del onboarding.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### ACC-P1-03 — Activación mínima

- **Prioridad:** P1.
- **Problema:** solicitar datos personales no necesarios aumenta abandono y riesgo de privacidad.
- **Evidencia:** decisión consolidada.
- **Cambio propuesto:** requerir únicamente nombre, apellidos y contraseña; marcas y datos deportivos son opcionales.
- **Criterios de aceptación:**
  - Una invitación válida permite completar los tres datos obligatorios y activar la cuenta.
  - La ausencia de marcas no bloquea la activación.
  - Una invitación caducada no activa y ofrece solicitar reenvío.
- **Dependencias:** ACC-P1-01.
- **Riesgo:** abandono o recogida excesiva de datos.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### ACC-P1-04 — Ciclo de desactivación, reactivación y eliminación

- **Prioridad:** P1, con ejecución condicionada por BET-P0-01.
- **Problema:** desactivar, borrar y corregir un rol aparecían mezclados.
- **Evidencia:** decisiones consolidadas en [`auditoria-documental.md`](auditoria-documental.md#41-roles-y-cuentas).
- **Cambio propuesto:** separar claramente desactivación reversible y eliminación sujeta a privacidad.
- **Criterios de aceptación:**
  - Al desactivar un Alumno pierde acceso y emails, conserva historial, cancela cambios de grupo y al reactivarse queda sin grupo.
  - Al desactivar un Entrenador pierde asignaciones; su autoría se conserva y se avisa al Administrador si un grupo queda sin Entrenador.
  - No se puede desactivar al último Administrador activo.
  - Solo un Administrador elimina cuentas no administrativas y confirma escribiendo el email exacto.
  - Tras la eliminación aprobada, el email puede reutilizarse.
  - La eliminación de Administradores y las solicitudes de los Alumnos se ejecutan por runbook.
- **Dependencias:** BET-P0-01 y BET-P0-02.
- **Riesgo:** pérdida de trazabilidad o borrado incompatible con obligaciones legales.
- **Estado:** Decidido funcionalmente; bloqueado en su semántica de datos.
- **Responsable:** Pendiente de asignar.

---

## P1 — Roles, tags y grupos

### ORG-P1-01 — Permisos exclusivos y jerarquía Administrador–Entrenador

- **Prioridad:** P1.
- **Problema:** la documentación otorgaba capacidades distintas a los mismos roles.
- **Evidencia:** [`auditoria-documental.md`](auditoria-documental.md#41-roles-y-cuentas).
- **Cambio propuesto:** aplicar una matriz única de permisos.
- **Criterios de aceptación:**
  - Administrador incluye todas las funciones de Entrenador y puede gestionar todo el club.
  - Entrenador solo consulta y gestiona sus grupos.
  - Alumno solo accede a sus datos y entrenamientos.
  - Personal del club y Alumno son perfiles mutuamente excluyentes.
  - El rol no puede cambiar después de crear la cuenta.
  - Puede haber varios Administradores y cualquier Administrador puede invitar otro con confirmación reforzada y auditoría.
- **Dependencias:** ACC-P1-04.
- **Riesgo:** accesos indebidos o necesidad de cuentas duplicadas.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### ORG-P1-02 — Tags configurables y sugerencia de grupo

- **Prioridad:** P1.
- **Problema:** no estaba clara la autoridad sobre tags ni el efecto de las reglas.
- **Evidencia:** [`../docs/vision.md`](../docs/vision.md) y decisiones consolidadas.
- **Cambio propuesto:** mantener tags configurables, administrados exclusivamente por el Administrador, para sugerir grupos sin asignación automática.
- **Criterios de aceptación:**
  - El Administrador crea, renombra y archiva tipos y valores de tag.
  - Solo el Administrador modifica los tags de un Alumno.
  - Las reglas aplican `Y` entre tipos y `O` entre valores del mismo tipo.
  - No existen negaciones ni expresiones anidadas.
  - Si coinciden varios grupos se muestran todos y el Administrador elige uno.
  - Cambiar tags no mueve al Alumno; genera una recomendación de revisión si ya no encaja.
- **Dependencias:** ORG-P1-01.
- **Riesgo:** cambios automáticos de planificación o constructor de reglas excesivamente complejo.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### ORG-P1-03 — Pertenencia única y cambio programado de grupo

- **Prioridad:** P1.
- **Problema:** distintos documentos permitían varios grupos o un único plan activo.
- **Evidencia:** [`../docs/vision.md`](../docs/vision.md), [`../docs/wireframes/04-group-builder.md`](../docs/wireframes/04-group-builder.md) y decisión consolidada.
- **Cambio propuesto:** un Alumno pertenece como máximo a un grupo y los cambios son efectivos en lunes.
- **Criterios de aceptación:**
  - Un Alumno activo puede estar sin grupo; solo los Administradores lo gestionan mientras no tenga asignación.
  - La incorporación inicial a un grupo y cualquier cambio son efectivos un lunes.
  - Solo existe un cambio pendiente, modificable o cancelable antes de aplicarse.
  - Hasta la fecha efectiva conserva el plan anterior; desde ella recibe el nuevo.
  - Historial y reportes pasados no cambian.
  - Los Entrenadores del nuevo grupo reciben acceso al historial completo, incluidos comentarios; los anteriores lo pierden.
  - Un grupo solo se archiva cuando no tiene alumnos y su historial se conserva.
- **Dependencias:** ORG-P1-01 y ORG-P1-02.
- **Riesgo:** mezcla de planes o pérdida de continuidad deportiva.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### ORG-P1-04 — Asignación de Entrenadores a grupos

- **Prioridad:** P1.
- **Problema:** no existía una regla única de acceso por grupo.
- **Evidencia:** decisión consolidada.
- **Cambio propuesto:** relación muchos-a-muchos entre Entrenadores y grupos, administrada por Administradores.
- **Criterios de aceptación:**
  - Un Entrenador puede pertenecer a varios grupos y un grupo tener varios Entrenadores.
  - Solo los asignados consultan planes, alumnos, reportes, comentarios y alertas del grupo.
  - El Administrador mantiene acceso global.
  - Desasignar o desactivar revoca el acceso sin borrar autoría histórica.
- **Dependencias:** ORG-P1-01.
- **Riesgo:** exposición de información de alumnos sin relación operativa.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

---

## P1 — Planificación y prescripción

### PLAN-P1-01 — Ciclo de la semana de entrenamiento

- **Prioridad:** P1.
- **Problema:** publicación, edición, pasado y concurrencia no tenían reglas coherentes.
- **Evidencia:** [`../docs/wireframes/05-coach-week-editor.md`](../docs/wireframes/05-coach-week-editor.md) y decisiones consolidadas.
- **Cambio propuesto:** gestionar semanas naturales con borrador, publicación y versiones.
- **Criterios de aceptación:**
  - Solo se crean o publican la semana actual y la siguiente, en `Europe/Madrid`.
  - La publicación inicial exige que cada día sea Sesión o Descanso.
  - La publicación es manual, inmediata y no puede deshacerse.
  - Días pasados y día actual no se editan ni eliminan por el Entrenador.
  - Un día futuro puede editarse, recibir una sesión nueva o quedar vacío al eliminar una existente.
  - Cada guardado registra autor, fecha y versión y genera un resumen de cambios.
  - Una edición basada en versión antigua se rechaza y exige recargar.
  - Máximo una sesión por Alumno y día.
- **Dependencias:** ORG-P1-03 y ORG-P1-04.
- **Riesgo:** sobrescritura silenciosa o planes retroactivos.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### PLAN-P1-02 — Copiar la semana anterior

- **Prioridad:** P1.
- **Problema:** sin copia, el tiempo validado de planificación supera el umbral aceptable.
- **Evidencia:** [`../docs/wireframes/findings.md`](../docs/wireframes/findings.md).
- **Cambio propuesto:** copiar el plan común de la semana anterior publicada.
- **Criterios de aceptación:**
  - Solo se copia hacia la semana siguiente.
  - El origen debe estar publicado y el destino completamente vacío.
  - Se copian sesiones y descansos comunes.
  - No se copian variantes individuales, reportes, comentarios ni alertas.
  - La copia crea un borrador editable; no publica automáticamente.
- **Dependencias:** PLAN-P1-01.
- **Riesgo:** pérdida de eficiencia o reproducción accidental de información individual.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### PLAN-P1-03 — Constructor estructurado de sesiones

- **Prioridad:** P1.
- **Problema:** el texto libre no permite validar ni calcular de forma consistente la prescripción.
- **Evidencia:** decisiones consolidadas en [`auditoria-documental.md`](auditoria-documental.md#43-planificación-y-sesiones).
- **Cambio propuesto:** crear sesiones como secuencias ordenadas de bloques tipados.
- **Criterios de aceptación:**
  - Tipos disponibles: Calentamiento, Carrera continua, Intervalo, Recuperación y Vuelta a la calma.
  - Cada bloque termina por distancia métrica o duración.
  - Cada bloque admite una sola intensidad: ninguna, ritmo absoluto, ritmo relativo o Z1–Z5.
  - Z1–Z5 se muestra como referencia textual; no se almacenan pulsaciones ni se calculan rangos.
  - Se puede repetir un conjunto de bloques con un único nivel de repetición.
  - No hay repeticiones anidadas, texto libre, Técnica ni Fuerza.
- **Dependencias:** PLAN-P1-01 y PLAN-P1-04.
- **Riesgo:** modelo demasiado limitado para sesiones reales; debe validarse con entrenadores antes de cerrar UX.
- **Estado:** Decidido; requiere validación de usabilidad.
- **Responsable:** Pendiente de asignar.

### PLAN-P1-04 — Marcas y ritmos relativos

- **Prioridad:** P1.
- **Problema:** el principal diferenciador dependía de datos privados, ausentes o ambiguos.
- **Evidencia:** [`../docs/vision.md`](../docs/vision.md) y decisiones consolidadas.
- **Cambio propuesto:** mantener una marca vigente por distancia y resolver ritmos relativos cuando sea posible.
- **Criterios de aceptación:**
  - Distancias fijas: 5K, 10K, Media maratón y Maratón.
  - Cada marca contiene tiempo total y fecha.
  - Solo el Alumno crea o sustituye su marca; Administradores y Entrenadores autorizados ven el valor exacto.
  - Solo se muestra una marca vigente por distancia; la anterior queda únicamente en auditoría técnica.
  - El ritmo base es `tiempo total / distancia`, el delta se suma o resta y se redondea al segundo por kilómetro.
  - Si falta la marca, el Alumno ve la fórmula original, por ejemplo `Marca 10K + 10 s/km`.
  - La ausencia no bloquea publicación ni genera aviso al Entrenador.
- **Dependencias:** PLAN-P1-03 y BET-P0-01 para visibilidad y retención.
- **Riesgo:** alumnos sin marca reciben una prescripción no resuelta; riesgo aceptado por decisión de producto.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### PLAN-P1-05 — Variaciones individuales

- **Prioridad:** P1.
- **Problema:** el plan común no permite adaptar una sesión sin crear grupos artificiales.
- **Evidencia:** decisión consolidada.
- **Cambio propuesto:** permitir al Entrenador sustituir una sesión por una variación individual.
- **Criterios de aceptación:**
  - La variante solo afecta al Alumno elegido.
  - El Alumno ve directamente la sesión resultante, sin etiqueta `Personalizado para ti`.
  - Si cambia la sesión base, la variante se conserva y el Entrenador recibe aviso para revisarla.
  - Si se elimina la sesión base, el Entrenador decide por cada variante si conservarla o eliminarla antes de confirmar.
  - La autoría y versiones quedan registradas.
- **Dependencias:** PLAN-P1-01 y PLAN-P1-03.
- **Riesgo:** variantes obsoletas respecto al plan común.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### PLAN-P1-06 — Reprogramación personal dentro de la semana

- **Prioridad:** P1.
- **Problema:** “mover o saltar” una sesión no definía destino, permisos ni conflictos.
- **Evidencia:** [`../docs/backlog.md`](../docs/backlog.md) y decisión consolidada.
- **Cambio propuesto:** permitir al Alumno mover su propia sesión a un día libre posterior.
- **Criterios de aceptación:**
  - Solo puede mover una sesión todavía no reportada hasta finalizar su día planificado.
  - El destino debe ser posterior, libre y pertenecer a la misma semana.
  - Un día ocupado rechaza el movimiento y solicita otro día.
  - El movimiento no altera el plan común del grupo.
  - No se pueden mover sesiones a días pasados ni fuera de la semana.
- **Dependencias:** PLAN-P1-01.
- **Riesgo:** doble carga o alteración del plan del grupo.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

---

## P1 — Reportes, disponibilidad y alertas

### REP-P1-01 — Reporte verificable de sesión

- **Prioridad:** P1.
- **Problema:** resultado, RPE y obligatoriedad eran contradictorios.
- **Evidencia:** [`../docs/wireframes/07-student-report.md`](../docs/wireframes/07-student-report.md) y decisiones consolidadas.
- **Cambio propuesto:** normalizar resultado y RPE.
- **Criterios de aceptación:**
  - Resultado obligatorio: Realizada, Parcial u Omitida.
  - RPE obligatorio en Realizada y Parcial; no aparece en Omitida.
  - Escala: 1 Muy suave, 2 Suave, 3 Moderado, 4 Duro, 5 Máximo.
  - Molestia y comentario son opcionales.
  - Solo se puede reportar desde el inicio del día planificado hasta el final de la semana natural.
  - Durante la semana actual el Alumno puede corregir el reporte y se conservan versiones.
  - No se puede reportar una sesión futura.
- **Dependencias:** PLAN-P1-01.
- **Riesgo:** datos incoherentes y alertas falsas.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### REP-P1-02 — Disponibilidad declarada por el Alumno

- **Prioridad:** P1.
- **Problema:** estados como lesión o posparto mezclaban clasificación, salud y automatismos.
- **Evidencia:** decisión consolidada.
- **Cambio propuesto:** usar Disponible y No disponible con comentario opcional.
- **Criterios de aceptación:**
  - Solo el Alumno cambia su disponibilidad.
  - `No disponible` genera una alerta informativa.
  - El cambio no pausa, elimina ni modifica sesiones automáticamente.
  - Entrenadores asignados y Administradores ven el estado vigente.
- **Dependencias:** ALR-P1-01 y BET-P0-01.
- **Riesgo:** interpretar un estado operativo como diagnóstico médico.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### ALR-P1-01 — Reglas estructuradas de alertas

- **Prioridad:** P1.
- **Problema:** las alertas pretendían inferir ritmo o intención desde datos no fiables.
- **Evidencia:** [`../docs/wireframes/08-coach-alerts.md`](../docs/wireframes/08-coach-alerts.md) y decisiones consolidadas.
- **Cambio propuesto:** generar alertas únicamente con eventos estructurados.
- **Criterios de aceptación:**
  - RPE: dos últimas sesiones Realizadas o Parciales con RPE 5.
  - Molestia: dos últimas sesiones Realizadas o Parciales con indicador de molestia; una Omitida, ausencia de reporte o reporte sin molestia rompe la secuencia.
  - Inactividad: umbral global del club en sesiones planificadas consecutivas sin reportar; valor inicial 2.
  - Omitidas: umbral global independiente; valor inicial 2 consecutivas.
  - Parciales: umbral global independiente; valor inicial 3 consecutivas.
  - Solo el Administrador modifica umbrales.
  - No se analiza texto libre ni se generan desviaciones de ritmo.
  - Descansos, días vacíos y sesiones todavía no vencidas se excluyen.
- **Dependencias:** REP-P1-01 y ORG-P1-01.
- **Riesgo:** exceso de ruido o falsa sensación de monitorización médica.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### ALR-P1-02 — Flujo de gestión de alertas

- **Prioridad:** P1.
- **Problema:** no existía propiedad ni cierre trazable de las alertas.
- **Evidencia:** decisión consolidada.
- **Cambio propuesto:** estados Pendiente, En seguimiento y Resuelta.
- **Criterios de aceptación:**
  - Una alerta En seguimiento se asigna a un Entrenador y puede reasignarse explícitamente.
  - Entrenadores asignados gestionan las de su grupo; Administradores gestionan todas.
  - Mientras una alerta del mismo tipo esté abierta, nuevos eventos actualizan su contador sin duplicarla.
  - Si estaba resuelta y vuelve a cumplirse el umbral, se crea otra vinculada a la anterior.
  - Una corrección de reporte que invalida el origen la cierra como `Resuelta por corrección` sin borrar el historial.
  - Cada transición registra actor, fecha y comentario opcional.
- **Dependencias:** ALR-P1-01 y ORG-P1-04.
- **Riesgo:** alertas ignoradas o atención duplicada.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

---

## P1 — Comunicación y notificaciones

### COM-P1-01 — Conversación contextual por sesión

- **Prioridad:** P1.
- **Problema:** responder fuera de la aplicación rompe el contexto del seguimiento.
- **Evidencia:** [`../docs/journeys/coach-runner.md`](../docs/journeys/coach-runner.md) y decisiones consolidadas.
- **Cambio propuesto:** conversación cronológica de texto vinculada a la sesión.
- **Criterios de aceptación:**
  - Participan el Alumno, Entrenadores asignados y Administradores.
  - Los Entrenadores del nuevo grupo reciben acceso a conversaciones históricas al hacerse efectivo el cambio; los anteriores lo pierden.
  - No hay adjuntos, imágenes, menciones, reacciones, edición ni eliminación desde la interfaz.
  - Una corrección se publica como un nuevo mensaje.
  - Autor y fecha quedan visibles y auditados.
- **Dependencias:** ORG-P1-03, ORG-P1-04 y BET-P0-01.
- **Riesgo:** exposición de datos personales a personal no autorizado.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### NOT-P1-01 — Centro interno y preferencias de email

- **Prioridad:** P1.
- **Problema:** la entrega de cambios y comentarios dependía de canales externos o reglas inconsistentes.
- **Evidencia:** decisión consolidada.
- **Cambio propuesto:** centro de notificaciones interno obligatorio y emails configurables por categoría.
- **Criterios de aceptación:**
  - Publicaciones, cambios de plan, comentarios y alertas crean notificaciones internas Leídas/No leídas.
  - Las notificaciones internas no pueden desactivarse.
  - Emails de plan del Alumno: activados por defecto y configurables.
  - Emails de comentarios: preferencia independiente, activada por defecto para cada usuario.
  - Emails de alertas de Entrenador: activados por defecto.
  - Emails globales de alertas de Administrador: desactivados por defecto.
  - Un guardado con varios cambios crea una sola notificación resumida por semana.
  - Invitación, acceso y seguridad no dependen de preferencias.
- **Dependencias:** ACC-P1-01, PLAN-P1-01, COM-P1-01 y ALR-P1-02.
- **Riesgo:** alumnos entrenando con una versión antigua o saturación de correo.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

---

## P1 — Dashboard e histórico

### DASH-P1-01 — Dashboard operativo con tendencias

- **Prioridad:** P1.
- **Problema:** el panel de alertas no cubre el análisis agregado e histórico decidido para el MVP.
- **Evidencia:** decisión consolidada y [`../docs/wireframes/findings.md`](../docs/wireframes/findings.md).
- **Cambio propuesto:** dashboard por rango temporal con desglose hasta Alumno.
- **Criterios de aceptación:**
  - Muestra conteos de Realizadas, Parciales, Omitidas, Sin reportar y Pendientes.
  - Muestra RPE medio y distribución 1–5.
  - Muestra alumnos con molestias consecutivas, Disponibles/No disponibles y alertas por tipo y estado.
  - Representa la evolución semanal de esas métricas.
  - El rango inicial son las últimas 12 semanas y se puede seleccionar cualquier rango histórico disponible.
  - Entrenadores ven solo sus grupos; Administradores ven el club y filtran por grupo.
  - Cada resultado permite consultar los alumnos que lo componen.
  - Día actual y futuro cuentan como Pendiente; solo un día finalizado sin reporte cuenta como Sin reportar.
  - Descansos y días vacíos no intervienen.
  - No existe exportación CSV o PDF.
- **Dependencias:** REP-P1-01, REP-P1-02, ALR-P1-01 y ORG-P1-04.
- **Riesgo:** ampliación sustancial de alcance y coste de consultas; debe estimarse antes de comprometer fecha.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

### HIST-P1-01 — Historial accesible y transferencia de acceso

- **Prioridad:** P1.
- **Problema:** no estaban definidos el horizonte visible ni el acceso tras un cambio de grupo.
- **Evidencia:** decisión consolidada.
- **Cambio propuesto:** conservar el historial completo del Alumno con acceso según su grupo vigente.
- **Criterios de aceptación:**
  - El Alumno consulta todas sus semanas pasadas, la actual y la siguiente solo si está publicada.
  - El Administrador consulta todo el historial del club.
  - Los Entrenadores del grupo vigente consultan historial, reportes y comentarios completos.
  - El equipo entrenador anterior pierde acceso cuando el cambio de grupo se hace efectivo.
  - Desactivar una cuenta no elimina su historial.
- **Dependencias:** ORG-P1-03, COM-P1-01 y BET-P0-01.
- **Riesgo:** pérdida de continuidad o acceso excesivo a datos históricos.
- **Estado:** Decidido.
- **Responsable:** Pendiente de asignar.

---

## P2 — Evoluciones posteriores

| ID | Mejora | Motivo para aplazar |
|---|---|---|
| EVO-P2-01 | Importación CSV con mapeo y corrección | El pegado de emails cubre el alta masiva básica con menor complejidad. |
| EVO-P2-02 | Histórico deportivo visible de marcas | El MVP solo necesita una referencia vigente para calcular ritmos. |
| EVO-P2-03 | Biblioteca de sesiones y semanas | Copiar la semana anterior cubre el ahorro operativo validado. |
| EVO-P2-04 | Técnica y Fuerza | Requieren catálogo o instrucciones suficientes para prescribir ejercicios. |
| EVO-P2-05 | Rangos personales de frecuencia cardiaca | El MVP solo muestra Z1–Z5 como referencia textual. |
| EVO-P2-06 | Exportación del dashboard | No mejora el bucle central del entrenamiento. |
| EVO-P2-07 | Internacionalización | El piloto opera únicamente en español. |
| EVO-P2-08 | Login con Google | No resuelve un dolor prioritario del flujo central. |
| EVO-P2-09 | Aplicación móvil nativa | La web responsive cubre móvil y escritorio. |
| EVO-P2-10 | Multi-club | Solo debe generalizarse después de validar el club piloto. |

---

## Fuera de alcance de esta versión

| Capacidad | Clasificación |
|---|---|
| Cuotas, cobros, facturas, suscripciones y renovaciones | Fuera de alcance |
| Contabilidad | Fuera de alcance |
| Gestión comercial de membresías | Fuera de alcance |
| Inscripciones y venta de dorsales | Fuera de alcance |
| Organización logística de carreras y eventos | Fuera de alcance |
| Control de asistencia presencial | Fuera de alcance |
| Comunicación general del club | Fuera de alcance |
| Web pública, tienda, patrocinadores y merchandising | Fuera de alcance |
| Ciclismo, natación, gimnasio y multideporte | Fuera de alcance |

## Revisión de calidad

- **Cambio de alcance:** declarado expresamente en DOC-P0-01.
- **Requisitos verificables:** cada P1 identifica actor, comportamiento y resultados observables; los límites jurídicos permanecen bloqueados.
- **Criterios de aceptación:** incluyen éxito y los errores o límites funcionales que ya fueron decididos. No se afirma que estén implementados ni probados.
- **Preguntas bloqueantes:** las únicas restantes están recogidas en [`decisiones-pendientes.md`](decisiones-pendientes.md).
- **Revisor humano:** Responsable de producto para alcance; Arquitectura y QA para ejecutabilidad; Privacidad/DPO para beta.
