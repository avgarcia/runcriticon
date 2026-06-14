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
| **H4** — taxonomía nivel × distancia × carrera | **Refutada parcialmente** (ver [Cierre del card-sort](#cierre-del-card-sort-con-rg-y-vg) más abajo) | Card-sort con RG y VG (2026-05-17): de los tres ejes propuestos solo **nivel** es universal. *Distancia* y *carrera* no son cómo piensan los entrenadores reales — son cómo les hemos obligado a pensar. Emergen ejes nuevos no contemplados: terreno (asfalto/trail), estado (lesión/mantenimiento), tipo de objetivo (carrera/oposiciones/CACO). R16 confirmado al máximo (30-40% de grupos resultantes son micro-grupos). Decisión: pasar a tags libres en MVP. |
| **H5** *(nueva)* — el diferenciador real es **"un plan, ritmos por corredor"** (ritmos relativos a las marcas de cada uno) | **Confirmada (RG, VG) — consolidada en ronda 2 de wireframes (2026-05-27)** | RG la verbaliza casi como propuesta de producto: *"que las zonas de ritmo cambien automáticamente en el perfil de cada corredor según su última marca"*. JM y AVG lo piden indirectamente al frustrarse con planes genéricos del club. Validada en ronda 2 de wireframes: RG y VG confirman el modelo de delta sobre marca (*"10K + 10 s/km"*), no porcentajes. Activada como M19 en el MVP. Las marcas del alumno son privadas (M20). Pendiente validar con un segundo entrenador ajeno al club piloto para confirmar el sweet spot diferencial. |

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

- [x] Cerrar club piloto (RG o VG) y firmar compromiso de beta.
- [x] Hacer card-sort con 1-2 entrenadores para validar H4. → ver cierre más abajo.
- [x] Diseñar wireframes de las pantallas críticas del MVP (10 pantallas, HTML lo-fi). Validadas con 5 participantes en 2 rondas — ver [`docs/wireframes/findings.md`](../../wireframes/findings.md).
- [x] Hipótesis H5: validada informalmente con RG y VG en ronda 2 de wireframes (2026-05-27). Activada como M19+M20 en el MVP.

---

## Cierre del card-sort con RG y VG

> Sesiones realizadas el 2026-05-17 (RG) y 2026-05-15 (VG). Capturas en [`card-sort/RG-card-sort.md`](card-sort/RG-card-sort.md) y [`card-sort/VG-card-sort.md`](card-sort/VG-card-sort.md). Guion del ejercicio en [`card-sort.md`](card-sort.md).

### Lo que pasó

**RG (500 alumnos, 50 cartas)** — En la fase 1 (sort abierto) agrupó usando **únicamente nivel** (alto/medio/iniciación). **Ni distancia ni carrera aparecieron como criterio espontáneo.** En la fase 2 (sort cerrado con nuestra taxonomía) sufrió: produjo 12 celdas con 5 micro-grupos (≤ 2 alumnos) y varias celdas vacías. Coincidencia entre fases: 75%.

**VG (180 alumnos, 35 cartas)** — En la fase 1 creó 7 grupos por ejes **heterogéneos**: comunidad ("los de Maratón Valencia"), tipo de objetivo ("Opos", "Mantenimiento", "Iniciación CACO"), terreno ("Trail finde"), entrenamiento compartido ("ritmos rápidos del martes"), estado ("Opositores/Lesionados en el limbo"). Dejó 2 alumnos aparte (ultra-trail, embarazada). Citas demoledoras:

> *"Si obligo a separar por distancias exactas voy a acabar escribiendo 40 planes idénticos cambiando solo una línea. Me va a estallar la cabeza."* — VG, fase 2

> *"La montaña no la puedo meter en el saco de un 10k o una Maratón de asfalto. Es otro deporte."* — VG

### Veredicto aplicando la regla de decisión

- **H4 (taxonomía nivel × distancia × carrera) — refutada parcialmente.** El esqueleto solo aguanta para el grueso de asfalto en carrera. Nivel es el único eje universal; distancia y carrera son fricción real en los dos clubes piloto. La fase 2 produjo grupos *correctos* pero no *útiles*.
- **R16 (volumen rompe la taxonomía en micro-grupos) — confirmado al máximo.** 5/12 micro-grupos en RG, 4/12 en VG. VG estima que escalar a sus 180 alumnos generaría > 40 micro-grupos.
- **Ejes nuevos que emergen como críticos**:
  - **Terreno** (asfalto / trail). Vital para VG: *"otro deporte"*.
  - **Estado** (activo / lesión / mantenimiento / post-parto / etc.).
  - **Tipo de objetivo** (carrera / oposiciones / mantenimiento / CACO).
  - **Comunidad / día de entreno** (importante para VG, secundario para RG).

### Decisión (2026-05-17)

**Activar el plan B: tags libres como modelo de grupos del MVP**, no como evolución post-MVP.

Justificación:
- Los datos no dejan margen. Imponer la taxonomía nivel × distancia × carrera generaría rechazo en ambos clubes piloto.
- El modelo de datos ya estaba diseñado como tags clave-valor desde día 1 ([nota de arquitectura](../vision.md)), así que el coste técnico extra es **solo de UI** (editor de tags + constructor de grupos como queries), no de base de datos ni de migración.
- Hacerlo ahora, antes de programar, es 10x más barato que retrofitearlo tras el MVP.

### Cambios derivados (aplicados en esta misma actualización)

- `vision.md` — sección "Modelo de grupos" reescrita: tags libres definidos por el admin del club, grupos como consultas sobre tags.
- `backlog.md` — bloque 1 reescrito: desaparece M5 (clasificación nivel × distancia × carrera) y M6 (grupos sugeridos automáticamente por la taxonomía). En su lugar: M4 (definir taxonomía del club: tags y valores), M5 (asignar tags al alumno), M6 (crear grupo como consulta sobre tags). Añadido un MUST de fusión / sugerencia de fusión de micro-grupos para neutralizar R16.
- `risks.md` — R3b cerrado (taxonomía rígida ya no es la solución). R16 mitigado por el cambio de modelo y por la nueva funcionalidad de fusión. Añadido R17 (sin tags pre-cargados sensatos, el admin se atasca al inicio).
- `journeys/admin-setup.md` — rehecha la puesta en marcha alrededor del flujo de tags.
- Personas — pequeños ajustes para reflejar el nuevo modelo.
