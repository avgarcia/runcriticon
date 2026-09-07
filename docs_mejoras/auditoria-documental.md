# Auditoría documental consolidada

## 1. Resumen ejecutivo

### Resultado de la auditoría original

**Madurez global: 4,7/10. Veredicto original: NO-GO para construir el MVP completo o iniciar una beta con datos reales.**

La documentación demuestra investigación real y una arquitectura técnica desarrollada, pero no contenía una fuente única y ejecutable de producto. Backlog, journeys, wireframes, ADR y plan de implementación resolvían de forma distinta decisiones sobre alcance, roles, grupos, marcas, alertas e importación.

| Eje | Peso | Puntuación original | Diagnóstico |
|---|---:|---:|---|
| Negocio | 30 % | 5,5 | Dolor real, pero posicionamiento y límites comerciales sin cerrar. |
| Consistencia funcional y UX | 30 % | 4,0 | Contradicciones bloqueantes en roles, grupos, planes, alertas y marcas. |
| Calidad y verificabilidad | 30 % | 4,0 | Mucho detalle, pero varias fuentes de verdad divergentes. |
| Preparación técnica y operativa | 10 % | 6,5 | Arquitectura sólida; privacidad y runbooks incompletos. |
| **Global ponderada** | | **4,7/10** | **No preparado en el momento de la auditoría.** |

### Estado después de las decisiones recogidas

La principal ambigüedad de negocio ha quedado resuelta:

> Runcriticon se posiciona en el MVP como gestor de entrenamientos para un club de running.

Pagos, membresías comerciales y operativa general del club quedan fuera. Las decisiones funcionales principales también están cerradas en esta carpeta. El proyecto continúa en **NO-GO documental** hasta trasladarlas a las fuentes normativas de `docs` y resolver la preparación jurídica y operativa para beta.

## 2. Evidencia de negocio y posicionamiento

El dolor de entrenamiento está respaldado por investigación: segmentación de alumnos, repetición de la planificación y gestión por excepción aparecen como problemas frecuentes en [`research/findings.md`](../docs/research/findings.md) y [`wireframes/findings.md`](../docs/wireframes/findings.md).

La auditoría detectó que la expresión “gestión de clubes” colocaba el producto frente a soluciones con una cobertura funcional mucho más amplia. La fotografía competitiva oficial utilizada fue:

- [TrainingPeaks](https://www.trainingpeaks.com/get-started-coach/): planificación y seguimiento de atletas.
- [Final Surge](https://site.finalsurge.com/teams.cshtml): calendarios, planes y trabajo con equipos.
- [Pitchero](https://www.pitchero.com/): membresías, pagos y comunicación general del club.
- [RunClub HQ](https://runclubhq.com/): membresía, pagos, asistencia, eventos y mensajería.

La decisión adoptada evita competir en dos categorías simultáneas. El MVP se concentra en el bucle:

`organizar alumnos → planificar → publicar → ejecutar → reportar → detectar excepciones → responder`.

## 3. Matriz de hallazgos y resolución

| Módulo | Problema detectado | Impacto | Resolución adoptada | Confianza |
|---|---|---|---|---|
| Posicionamiento | La promesa de gestión integral no coincidía con las capacidades descritas. | Crítico | El MVP se define como gestor de entrenamientos. La gestión comercial del club queda fuera. | Alta |
| Altas masivas | El journey consideraba esencial el CSV, pero el plan lo excluía. | Crítico | El MVP usará pegado de emails con validación y revisión. CSV pasa a evolución posterior. | Alta |
| Autenticación | El backlog incluía Google mientras el ADR y el plan lo aplazaban. | Alto | Google queda fuera. Cualquier usuario activo podrá usar contraseña o enlace mágico. | Alta |
| Roles | La documentación mezclaba roles acumulables, herencia y cuentas separadas. | Crítico | Administrador hereda funciones de Entrenador. Administrador, Entrenador y Alumno son perfiles exclusivos; el rol es inmutable. | Alta |
| Pertenencia a grupos | Visión y personas permitían varios grupos, mientras un wireframe imponía un único plan activo. | Crítico | Un Alumno pertenece como máximo a un grupo. Los cambios son programados y efectivos en lunes. | Alta |
| Clasificación | No estaba claro si los tags asignaban automáticamente o solo sugerían. | Alto | Los tags son configurables; solo los modifica el Administrador. El sistema sugiere y el Administrador decide. | Alta |
| Planificación semanal | Copiar la semana anterior era esencial en validación, pero no aparecía como MUST verificable. | Alto | Copiar semana anterior publicada a una semana siguiente vacía es obligatorio. | Alta |
| Edición publicada | Faltaban reglas para concurrencia, pasado, cancelación y variantes. | Crítico | Control optimista, días actuales/pasados cerrados, eliminación futura auditable y resolución explícita de variantes. | Alta |
| Constructor de sesiones | La documentación permitía demasiado texto libre para datos que debían calcularse. | Alto | Constructor estructurado por bloques, un nivel de repetición y una intensidad por bloque. | Alta |
| Ritmos relativos | El entrenador no podía verificar marcas y las ausencias no estaban resueltas. | Crítico | Todos pueden ver la marca; solo el Alumno la edita. Sin marca se muestra la fórmula sin resolver y no se avisa al entrenador. | Alta sobre la decisión; media sobre su resultado de adopción |
| Reportes y RPE | RPE aparecía como opcional, obligatorio y con sentidos opuestos. | Crítico | Escala única 1–5 creciente; obligatoria en realizada/parcial y no aplicable en omitida. | Alta |
| Alertas | Se pretendían inferencias desde texto libre o datos de ritmo inexistentes. | Crítico | Solo señales estructuradas: RPE, molestia, inactividad, parciales, omitidas y disponibilidad. | Alta |
| Comentarios | El fallback a email/WhatsApp impedía cerrar el seguimiento. | Alto | Conversación textual por sesión dentro de la aplicación, sin adjuntos ni edición. | Alta |
| Notificaciones | Publicación, comentarios y alertas no tenían un canal consistente. | Alto | Centro interno obligatorio y preferencias de email separadas. | Alta |
| Dashboard | El panel por excepción no cubría la petición posterior de tendencias completas. | Alto | Dashboard P1 con histórico por rango, desglose por Alumno y métricas definidas; sin exportación. | Alta |
| Privacidad | RAT, decisiones jurídicas, retención y runbooks estaban pendientes. | Crítico | Se mantiene como gate P0 de beta y requiere revisión jurídica y operativa. | Alta |
| Estado documental | El plan decía “greenfield sin código”, había conteos incompatibles de MUST y enlaces rotos. | Alto | Debe corregirse al trasladar este backlog a `docs`; no se modifica en esta fase. | Alta |
| Gobierno ADR | ADR aceptados habían recibido cambios materiales pese a la regla de inmutabilidad. | Alto | Separar erratas de cambios normativos y usar ADR sucesor cuando cambie una decisión. | Alta |

## 4. Decisiones funcionales consolidadas

### 4.1 Roles y cuentas

- **Administrador:** incluye todas las capacidades de Entrenador y administra cuentas, grupos, asignaciones, tags y configuración global.
- **Entrenador:** gestiona únicamente los grupos a los que está asignado.
- **Alumno:** recibe, adapta dentro de los límites acordados y reporta entrenamientos.
- Administradores y Entrenadores no pueden actuar como Alumnos.
- El rol es inmutable. Un error de rol exige eliminar y crear de nuevo la cuenta.
- Puede haber varios Administradores; no se puede desactivar al último activo.
- La eliminación de un Administrador solo se ejecuta mediante runbook.

### 4.2 Grupos

- Un Alumno puede estar activo sin grupo, pero pertenece como máximo a uno.
- Los Entrenadores pueden gestionar varios grupos y un grupo puede tener varios Entrenadores.
- Los cambios de grupo se programan para un lunes, conservan el historial y transfieren su acceso al nuevo equipo entrenador.
- Solo puede existir un cambio pendiente por Alumno.
- Un grupo solo se archiva cuando no tiene alumnos.

### 4.3 Planificación y sesiones

- Semanas naturales, lunes a domingo.
- Solo se planifican la semana actual y la siguiente.
- Para publicar inicialmente, cada día debe ser sesión o descanso.
- Máximo una sesión por Alumno y día.
- Publicación manual e inmediata; no se puede despublicar.
- Solo se modifican días posteriores al actual.
- El Alumno puede mover una sesión no reportada a un día posterior libre de la misma semana.
- La sesión se compone de bloques ordenados: Calentamiento, Carrera continua, Intervalo, Recuperación y Vuelta a la calma.
- Cada bloque finaliza por distancia o duración y admite como máximo una intensidad: ninguna, ritmo absoluto, ritmo relativo o zona Z1–Z5.
- No hay instrucciones de texto libre, Técnica ni Fuerza en el MVP.

### 4.4 Seguimiento

- Estados de reporte: Realizada, Parcial y Omitida.
- RPE 1–5 obligatorio para Realizada y Parcial.
- Molestia y comentario opcionales.
- Los reportes se abren al comenzar el día y se cierran al terminar la semana.
- Se pueden corregir durante la semana actual, conservando versiones.
- Disponibilidad del Alumno: Disponible o No disponible; solo él la modifica.
- El estado no cambia automáticamente el plan.

### 4.5 Alertas

- RPE 5 en dos reportes consecutivos.
- Molestia en dos reportes consecutivos; no se diferencia zona corporal.
- Inactividad: umbral global configurable, inicialmente dos sesiones planificadas sin reportar.
- Omitidas: umbral global configurable, inicialmente dos consecutivas.
- Parciales: umbral global configurable, inicialmente tres consecutivas.
- Estados: Pendiente, En seguimiento y Resuelta.
- Una alerta En seguimiento tiene Entrenador responsable y puede reasignarse.
- Las correcciones de reporte pueden cerrarla automáticamente sin borrar el historial.

## 5. Puertas de calidad

| Puerta | Estado actual | Acción requerida | Revisor humano |
|---|---|---|---|
| Cambio de alcance | Listo para revisión | Confirmar formalmente el nuevo posicionamiento al actualizar `vision.md`. | Responsable de producto |
| Requisitos verificables | Requiere traslado | Sustituir los MUST contradictorios por las historias de `backlog-mejoras.md`. | Producto y QA |
| Criterios de aceptación | Listo para revisión humana | Refinar límites técnicos sin cambiar las decisiones aprobadas. | Producto, Arquitectura y QA |
| Preguntas funcionales bloqueantes | Resueltas para esta fase | No reabrirlas silenciosamente durante implementación. | Responsable de producto |
| Trazabilidad entre fases | Bloqueada | Actualizar visión, backlog, journeys, wireframes, ADR y plan en una fase posterior. | Product Ops |
| Terminología | Requiere traslado | Adoptar `Alumno` como término canónico en documentos e interfaz. | Producto y UX |
| Privacidad | Bloqueada para beta | Completar evidencias jurídicas, RAT, retención, borrado y runbooks. | Privacidad/DPO |

## 6. Supuestos e incertidumbres

- La auditoría evalúa preparación documental, no calidad del código ni cumplimiento legal efectivo.
- La disposición a pagar no está validada; confianza **media-baja**.
- La solución de pegado de emails necesita prueba de usabilidad con el club piloto; confianza **media**.
- La ausencia deliberada de avisos al entrenador por marcas faltantes puede reducir la eficacia de los ritmos relativos; riesgo aceptado, confianza **media**.
- El dashboard completo aumenta de forma notable el alcance del MVP. Su valor fue decidido, pero su coste debe estimarse antes de comprometer fecha; confianza **alta**.
- Los prototipos HTML se inspeccionaron por su fuente. Su fidelidad visual renderizada no fue verificada de manera independiente; confianza **media**.
