# Hallazgos y síntesis

> Cierre de la primera ronda de entrevistas (2026-05-13 a 2026-05-18).
>
> **Muestra**: 6 entrevistas — 2 entrenadores ([RG](interviews/RG-entrenador.md), [VG](interviews/VG-entrenador.md)) y 4 corredores ([AVG](interviews/AVG-corredor.md), [JM](interviews/JM-corredor.md), [LS](interviews/LS-corredor.md), [PC](interviews/PC-corredor.md)). Tamaño suficiente para detectar patrones, no para conclusiones estadísticas.
>
> **Calidad del dato**: notas semi-estructuradas, sin transcripción literal. Las citas seleccionadas se han revisado para evitar atribuciones cruzadas.

## Estado de las hipótesis

| Hipótesis | Estado | Evidencia |
|---|---|---|
| **H1** — gestionan en Excel/PDF/WhatsApp y duele | **Confirmada parcialmente** | Confirma rotunda en entrenadores con volumen (RG, 500 alumnos, 15-20 h/sem en WhatsApp); confirma en JM (recibe PDF por WhatsApp). Refuta en LS (novato puro, no gestiona nada). Refuta parcialmente en PC (élite, ya en TrainingPeaks, pero sufre fragmentación). VG refuta en su ficha pero su contexto (5 h/día en WhatsApp + app propia) sugiere que el dolor existe aunque no lo llame así. |
| **H2** — quieren plan personalizado, no genérico | **Confirmada (5/6)** | Confirma en todos menos LS. PC: *"un plan genérico no es que sea inútil, es peligroso"*. AVG: el dolor #1 es que el club entrena igual a todos preparando una maratón cuando él prepara un 10k. RG matiza: la quiere pero "inviable a mano" → pide automatización. |
| **H3** — diferenciador es flujo entrenador-corredor | **Confirmada (3/6) + sin info en el resto** | Confirma explícitamente JM, PC y RG (esta última: *"si el flujo permite que yo cree 10 planes raíz pero a cada corredor le llegue adaptado, compro la app mañana"*). VG y AVG sin info. LS refuta porque no quiere entrenador. |
| **H4** — taxonomía nivel × distancia × carrera | **Encaja, pero falta el card-sort para confirmar** | Señales positivas: RG ya divide a sus 500 alumnos en *"10 macro-grupos por niveles (Iniciación, Maratón, Trail, etc.)"* — coincide casi exactamente con los ejes propuestos. AVG pide expresamente *"planificar las carreras que quiero correr, en lugar de una planificación genérica"*. No se hizo card-sort formal en esta ronda. |
| **H5** *(nueva)* — el diferenciador real es **"un plan, ritmos por corredor"** (ritmos relativos a las marcas de cada uno) | **Hipótesis emergente, no validada todavía** | RG la verbaliza casi como propuesta de producto: *"que las zonas de ritmo cambien automáticamente en el perfil de cada corredor según su última marca"*. JM y AVG lo piden indirectamente al frustrarse con planes genéricos del club. Falta validar con más clubes que esto sea el sweet spot diferencial. |

## Top 5 dolores reales detectados

1. **Atención individual insostenible** (RG, VG): el entrenador con volumen pierde horas respondiendo *"¿a qué ritmo me toca a mí?"* por WhatsApp privado.
2. **Falta de feedback al entrenador** (RG, VG, JM): no hay forma sencilla de saber si el plan está funcionando ni quién está fallando. JM lo llama *"agujero negro del feedback"*.
3. **Desconexión plan ↔ reloj** (JM, PC, implícito en RG): el corredor copia a mano el plan al Garmin; al revés, los datos del reloj no llegan estructurados al entrenador.
4. **Falta de flexibilidad día a día** (JM, AVG): un imprevisto (trabajo, lesión, cansancio) obliga a una conversación por WhatsApp para reajustar la semana.
5. **Altas / bajas caóticas** (RG, VG): el alta y baja de un alumno no se propaga entre los sistemas (web de pagos, grupos de WhatsApp, listados).

## Patrones recurrentes (lo que aparece en varias entrevistas)

### P1 — Personalización masiva automatizable (= "un plan, ritmos por corredor")

Mencionado por **RG explícitamente** y por **AVG y JM implícitamente**. La idea: el entrenador publica **un solo plan** al grupo, pero los **ritmos objetivo se computan por corredor** según su marca más reciente (umbrales, ritmos de carrera, etc.). Combina escala (un plan, no 500) con sensación de personalización para el alumno.

> **Implicación**: esto es probablemente el verdadero diferenciador frente a *"otro TrainingPeaks pero peor"*. No está en el backlog actual. Ver H5 y la nueva entrada en COULD.

### P2 — Panel de alertas / feedback por excepción

**RG**: *"una pantalla que no me muestre los 500 entrenos, sino solo las alertas importantes: quién ha marcado que tiene molestias, quién lleva una semana sin entrenar, quién ha entrenado muy por encima de sus ritmos"*. **PC** la pide en forma de *"semáforo verde/amarillo/rojo"* de carga semanal.

> **Implicación**: la actual M15 ("vista de seguimiento por grupo") es una vista de cumplimiento, no un panel de alertas. **Hace falta un nuevo MUST.**

### P3 — Reajuste rápido por imprevisto del día a día

**JM**: *"botón de reajustar día: hoy no puedo, muéveme la sesión a mañana"*. **AVG**: si no puede ir al entreno tiene que escribir al entrenador por WhatsApp y a veces no responde.

> **Implicación**: el alumno necesita una acción de un click para marcar imprevistos sin que dependa de la respuesta del entrenador. **Hace falta un nuevo MUST.**

### P4 — Datos del reloj (Garmin / Apple Watch) llegando al sistema

**JM** y **PC** lo dan por descontado; **RG** lo quiere como input para las alertas. Hoy está en COULD. Probablemente debe subir como **SHOULD prioritario** (no MUST: el alta inicial puede vivir con reporte manual rápido).

### P5 — Comentarios contextuales, no chat general

**PC** quiere *"notas por intervalo del entrenamiento"*, no comentario general. **RG** quiere chat dentro de la ficha del atleta, no WhatsApp. **JM** sufre el chat de WhatsApp como "agujero negro". El [coach-runner journey](../journeys/coach-runner.md) ya lo recoge como "comentario contextual por sesión"; queda confirmado.

## Sorpresas (cosas que NO esperábamos)

- **PC (élite) descarta absolutamente la IA** que genere planes: *"a este nivel las decisiones las tiene que tomar mi entrenador"*. No es un riesgo para nosotros (la IA estaba ya en WON'T), pero confirma que **el target valora el humano, no el algoritmo**.
- **LS (novato puro) descarta cualquier conexión con entrenador**. Quiere rutas, audio-guía y motivación. **No es nuestro target en MVP**.
- **PC tampoco encaja**: ya está en TrainingPeaks Premium, sus dolores son métricas avanzadas (HRV, vatios, correlación rendimiento-salud). Competiríamos con TrainingPeaks en su terreno → **no target en MVP**.
- **Volumen real de los clubes**: RG gestiona **500 alumnos**, VG **decenas**. Nuestro modelo mono-club asumía decenas. **500 alumnos pone presión muy distinta** sobre el panel de alertas, el rendimiento de las vistas agregadas y el modelo de grupos (el "10 macro-grupos" de RG no encaja en una taxonomía libre por carrera objetivo, porque 500 alumnos preparando distintas carreras producen demasiados micro-grupos).

## Sweet spot del target validado

| Segmento | ¿Target MVP? | Por qué |
|---|---|---|
| **Entrenador de club con decenas-centenares de alumnos** (RG, VG) | **Sí, central** | Es donde más duele el dolor y donde tenemos clara propuesta de valor. |
| **Corredor amateur intermedio con entrenador del club** (JM, AVG) | **Sí, central** | Recibe plan, lo ejecuta, quiere comunicación fluida. Confirma todas las hipótesis menos las muy técnicas. |
| **Novato puro sin entrenador** (LS) | **No** | Quiere rutas, motivación, audio-guía. Otro producto. |
| **Élite con herramienta profesional ya en uso** (PC) | **No (en MVP)** | Ya tiene TrainingPeaks; competimos en su terreno. Roadmap a 12+ meses si crecemos. |

## Implicaciones para el backlog

| Movimiento propuesto | Justificación | Estado |
|---|---|---|
| **Añadir M17 — Panel de alertas del entrenador** (lesiones, inactividad, desviación de ritmos) | P2, RG y PC lo piden | Propuesto en `backlog.md` |
| **Añadir M18 — Reajuste de día por el alumno** (mover sesión, marcar imprevisto) | P3, JM y AVG | Propuesto en `backlog.md` |
| **Promover "Importación de actividad del reloj" a SHOULD-prioritario** (primero post-MVP) | P4 | Ajustado en `backlog.md` |
| **Añadir a COULD — "Ritmos relativos a marcas del corredor"** (un plan, ritmos por persona) | P1, posible diferenciador real (H5) | Propuesto en `backlog.md` y `vision.md` (H5) |
| Mantener M16 ("vista de salud del club") | Cubre el dolor agregado del admin | Sin cambio |
| **NO añadir** integraciones avanzadas tipo HRV / vatios / correlación métricas | PC lo pide pero está fuera de target en MVP | Permanece en WON'T implícito |

## Riesgos nuevos identificados

- **R14 — Target demasiado estrecho si excluimos novatos y élites**: la base potencial del producto puede ser menor de lo esperado.
- **R15 — Sin "ritmos relativos por marcas" somos *"otro gestor de planes más"***: si P1 emerge como el verdadero diferenciador y no lo abordamos, perdemos la batalla competitiva.
- **R16 — Volumen real de 500 alumnos rompe asunciones de UI**: el modelo de grupos taxonómico produce micro-grupos a esa escala. Validar con RG si se mantiene en su contexto.

Estos riesgos quedan registrados en [`risks.md`](../risks.md).

## Decisiones tomadas en esta síntesis

1. **No** se realiza una segunda ronda de entrevistas todavía. La muestra de 6 da señal suficiente para refinar el MVP.
2. **Sí** se hace card-sort con RG y VG antes de programar (H4 sigue sin validar formalmente).
3. Se confirma el alcance mono-club. RG (500 alumnos) y VG son candidatos a club piloto; conviene cerrar con uno de los dos.
4. La "personalización masiva automatizable" entra como hipótesis explícita H5; no entra a MUST hasta validar con un segundo entrenador.

## Próximos pasos

- [ ] Cerrar club piloto (RG o VG) y firmar compromiso de beta.
- [ ] Hacer card-sort con 1-2 entrenadores para validar H4.
- [ ] Diseñar wireframes de las 6 pantallas críticas (incluyendo el panel de alertas M17).
- [ ] Hipótesis H5: preparar una pregunta directa sobre "ritmos relativos a marcas" para futuras conversaciones con entrenadores.
