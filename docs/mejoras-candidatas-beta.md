# Mejoras candidatas — tramo previo a la beta

> **Estado**: borrador para decisión. Fecha: 2026-09-22.
> **Qué es**: el resultado de una mesa de producto con cinco perspectivas enfrentadas (entrenador, alumno, admin del club, producto/crecimiento e ingeniería), depurado a 15 candidatas con sus objeciones, su coste y la decisión concreta que queda pendiente en cada una.
> **Qué no es**: no es backlog. Nada de aquí entra en [`backlog.md`](backlog.md) hasta que se decida; la clasificación MoSCoW de este documento es una **propuesta**, no el backlog vigente.

## Punto de partida verificado

Los documentos de proceso ([`plan-implementacion-mvp.md`](plan-implementacion-mvp.md), el propio `CLAUDE.md`) siguen describiendo el proyecto como *«hito H0 en curso, `seguimiento` en scaffold»*. **Eso ya no es cierto** y conviene corregirlo antes de decidir nada, porque cambia por completo qué es caro y qué es barato:

- Los cinco módulos están implementados. `seguimiento` tiene marcas del alumno, plan resuelto, reporte de sesión, reajuste de día, alertas del entrenador, salud del club y cinco proyecciones locales.
- El frontend tiene ocho rutas vivas: `/mi-plan`, `/mi-cuenta`, `/mis-marcas`, `/coaches`, `/alumnos`, `/club`, `/planificacion`, `/alertas`.
- **Las 21 MUST del backlog están construidas o muy cerca, salvo M15**, aplazada conscientemente en [ADR-0015 §A4](adr/0015-temas-aplazados-fuera-del-mvp.md).

Lo que **no** existe hoy: importación de actividad del reloj, comentarios del entrenador sobre sesiones ejecutadas, plantillas de plan, gráficas de progreso del alumno, calendario mensual, histórico de marcas y **cualquier notificación de producto** — el email solo se usa en `identidad` para invitación, magic link y reseteo.

Consecuencia para la decisión: el siguiente tramo ya no es «terminar el MVP», es **qué llevamos a la beta con el club piloto**.

## Criterios para decidir

1. **Regla de oro del backlog**: si se sustituye por WhatsApp + un Excel durante 6 meses, no es MUST.
2. **Objetivos de [`vision.md`](vision.md)**: entrenadores activos, alumnos que reportan ≥ 3 sesiones/semana, tiempo del entrenador, NPS del admin, go/no-go al segundo club.
3. **Riesgo antes de la beta**: la beta trae datos personales reales. Lo que exponga datos de salud (Art. 9) no compite por prioridad, se corrige.
4. **Coste real**, en términos de lo que ya está construido: reutilizar una proyección o un canal existente es barato; crear entidad, evento o pantalla nueva no lo es.

## Tabla de decisión

| # | Mejora | Piden | MoSCoW propuesto | Coste | Decisión pendiente |
|---|---|---|---|---|---|
| M-01 | Copiar la semana anterior | Entrenador, Producto, Ingeniería | MUST | bajo | Ninguna: es la única sin objeciones. Solo confirmar que entra |
| M-02 | Alcance del entrenador sobre alumnos | Admin, Alumno | **Corrección, no mejora** | medio | Qué forma toma: ocultar tags o recortar el listado |
| M-03 | Dos grupos, mismo día: no perder la sesión | Ingeniería | MUST (versión mínima) | bajo | Si entra la versión mínima o se aplaza con disparador |
| M-04 | Responder al alumno desde la alerta | Ingeniería, Producto, Alumno | SHOULD ★ | bajo / medio | Si se paga la PR de cambio de ADR-0002 D9 |
| M-05 | Aviso por email al publicar el plan | Entrenador, Producto | SHOULD | medio | Si el aviso es por defecto o opt-in |
| M-06 | La alerta tiene estado | Entrenador | SHOULD | medio | Si entra «posponer» además de «atendida» |
| M-07 | Aviso neutro de consentimiento | Admin, Entrenador, Producto | SHOULD | bajo | Ninguna relevante |
| M-08 | Continuidad de las personalizaciones | Entrenador | SHOULD | medio | Cuántas semanas dura la sugerencia |
| M-09 | Captura de marca al reportar competición | Alumno | SHOULD | bajo | Ninguna relevante |
| M-10 | Rescate del alumno sin marca | Producto, Alumno | SHOULD | bajo | Ninguna relevante |
| M-11 | Temporada de carreras | Ingeniería, Admin | COULD | bajo | Si el club piloto arrastra catálogo de temporada anterior |
| M-12 | Lenguaje llano desacoplado del nivel | Alumno | COULD | bajo / medio | Si se hace la versión recortada o nada |
| M-13 | Registro de accesos del club | Admin, Alumno | COULD | bajo + paginación | Si es prerrequisito de la beta por RGPD o no |
| M-14 | El alumno elige su objetivo | Alumno | COULD | medio | Aplazar con disparador o entrar |
| M-15 | Excepciones manuales a la vista | Admin | COULD | bajo | Aplazar a cierre de temporada |

**Aviso de lectura**: la columna MoSCoW la firma esta síntesis, no la mesa. Los roles solo emitieron `sobrevive` / `sobrevive-modificada` / `descartada`. Únicamente M-01 tiene unanimidad real (4 de 4 `sobrevive`); M-03 la propuso un solo rol y dos pidieron recortarla.

---

## Fichas

### M-01 · Copiar la semana anterior (y mover una sesión de día)

- **Problema**: el entrenador construye la semana del grupo sesión a sesión, cada domingo, desde cero.
- **Evidencia**: la validación de wireframes lo cronometró — 14:05 (RG) y 11:20 (VG) sin atajo, por encima del objetivo de 10 minutos; 6:12 y 4:45 con él. `wireframes/findings.md` lo sentencia: *no es un extra, es lo que hace la pantalla viable*. Es el momento crítico 1 de [`journeys/coach-runner.md`](journeys/coach-runner.md): si publicar la primera semana cuesta más que el Excel, el entrenador no vuelve.
- **Alcance mínimo**: al crear el plan, opción de copiar las sesiones de la última semana publicada a ese grupo. Queda en **borrador** y sigue exigiendo publicar a mano. Se copian sesiones con su ritmo; no se copian personalizaciones ni el snapshot de membresía.
- **Condiciones de la mesa**: dejarlo en borrador (alumno, entrenador); no arrastrar personalizaciones ni snapshot (admin, ingeniería).
- **Coste y anclaje**: bajo. Es un parámetro en `CreateDraftPlanCommand`, sin entidad ni evento nuevos. Matiz verificado: *mover una sesión de día* **sí toca backend y contrato** — `UpdateSessionCommand.kt:61` fija `day = existing.day` a propósito, el día no es editable hoy.
- **Riesgo de no hacerlo**: es el único ítem de la mesa con una métrica de `vision.md` medida en segundos. Sin él, el domingo del entrenador sigue costando 14 minutos.
- **Riesgo de hacerlo**: abarata publicar un plan perezoso — el alumno recibe la semana anterior calcada. Lo mitiga el borrador obligatorio, no lo elimina.
- **Decisión pendiente**: ninguna de fondo. Si el «mover de día» entra en el mismo lote o se separa.
- **Recomendación**: entra. Es el mejor ratio valor/coste del documento.

### M-02 · Alcance real del entrenador sobre alumnos y grupos

- **Problema**: todo entrenador del club ve hoy la lista completa de alumnos con **todos** sus tags, incluido el tag pre-cargado `estado`, cuyos valores de ejemplo en [`vision.md`](vision.md) son *activo · lesión · post-parto · descanso*. Eso es dato de salud (Art. 9) visible para entrenadores que no llevan a ese alumno.
- **Evidencia (verificada en código)**: el KDoc de `ListStudentsQuery.kt:23` declara *«Ambos ven hoy todos los alumnos del club, no un subconjunto: la relación entrenador↔alumno todavía no existe»*. Esa premisa es **falsa hoy**: `CoachGroupLookup` ya se usa en `planificacion` y el panel de alertas sí está acotado. `ListGroupsQuery` arrastra el mismo hueco. Y el criterio de aceptación de **M3b** en [`backlog.md`](backlog.md) dice literalmente que *«el entrenador ve la misma lista limitada a los alumnos que le corresponden»*.
- **Naturaleza**: esto **no es una mejora funcional**. Es un criterio de aceptación incumplido en código ya desplegado, con una fuga de categoría especial delante de una beta con datos reales. No debería competir por capacidad con el resto de la tabla.
- **Dos formas posibles**:
  - *(a)* Acotar la lista de alumnos y de grupos a los que le corresponden por su relación con el grupo — lo que exige M3b textualmente. Le quita al entrenador una herramienta que hoy usa para localizar a cualquiera.
  - *(b)* Mantener el listado completo y **ocultar los tags** del alumno fuera de sus grupos. Cierra la fuga del Art. 9 con mucho menos impacto en plena beta, pero no cumple M3b al pie de la letra.
- **Condiciones de la mesa**: si se elige *(a)*, cubrir la baja de un compañero debe resolverse asignando el grupo (M8, ya existe) y no con un ticket al admin; el entrenador conserva el alta de alumno.
- **Coste**: medio. Autorización a nivel de objeto ([ADR-0009 D3](adr/0009-autorizacion.md)), sin tocar ningún ADR ni reabrir la matriz configurable.
- **Decisión pendiente**: *(a)* o *(b)*, y si entra antes de la beta o se acepta el riesgo documentado.
- **Recomendación**: *(b)* antes de la beta y *(a)* después, con el KDoc y la matriz corregidos en el mismo cambio para que la premisa obsoleta no vuelva a justificar nada.

### M-03 · El alumno que está en dos grupos ve sus dos sesiones

- **Problema**: si dos grupos publican plan para el mismo día, el alumno ve **una** sesión y pierde la otra en silencio; el entrenador que la publicó la contabiliza como «no reportada».
- **Evidencia (verificada en código)**: `ResolvedPlanReaderJdbc.kt:211` usa `SELECT DISTINCT ON (día efectivo)`, que se queda con una fila. [`vision.md`](vision.md) dice que pertenecer a varios grupos *«es lo normal»*. El aplazamiento está declarado en el KDoc de `ResolvedPlanReader`, **no** en [ADR-0015](adr/0015-temas-aplazados-fuera-del-mvp.md) ni en el backlog.
- **Alcance mínimo**: la vista «mi semana» muestra las dos sesiones con su grupo de origen, solo el día en que ocurre el conflicto. Sin elección obligatoria y sin alerta al entrenador.
- **Condiciones de la mesa**: la sesión no ejecutada **no** puede llamarse *saltada* — en el vocabulario del alumno eso es un fallo suyo, y aquí el fallo es de publicación (alumno). Nada de alerta nueva en el panel del entrenador: su valor es enseñar solo lo accionable (entrenador, producto). Si alguna vez se avisa al entrenador, sin nombrar el otro grupo, porque el nombre del grupo puede ser un estado de salud (admin).
- **Coste**: bajo. Lectura sobre `plan_resuelto_por_alumno`, que ya guarda `grupo_id` y `plan_id` por fila.
- **Riesgo de no hacerlo**: pérdida silenciosa de entrenos en el camino crítico, sin que ni el alumno ni el entrenador puedan detectarla. Mata la confianza en la vista «hoy», que es la pantalla con mejor resultado de toda la validación (2,8 s / 3 s / 4 s).
- **Decisión pendiente**: si entra o se aplaza formalmente. **En cualquier caso, el aplazamiento debe pasar del KDoc a ADR-0015**: hoy es una no-decisión que no está en el índice maestro.
- **Recomendación**: entra la versión mínima. Y, se decida lo que se decida, se indexa en ADR-0015.

### M-04 · Responder al alumno desde la alerta

- **Problema**: el panel de alertas es de solo lectura. El entrenador ve «molestias reportadas» y su única salida dentro del producto es **sobrescribirle la sesión**, porque el mensaje al alumno solo viaja soldado a una personalización. Así que vuelve a WhatsApp.
- **Evidencia**: momento crítico 3 de [`journeys/coach-runner.md`](journeys/coach-runner.md) — *si el entrenador no comenta nada en 2-3 semanas, el alumno deja de reportar*. Necesidad #4 de la persona de Marta. Hallazgo P5 de [`research/findings.md`](research/findings.md).
- **Alcance mínimo**: desde la alerta, el entrenador escribe un mensaje que el alumno ve junto a su sesión, sin que la sesión cambie. Plantillas de respuesta **opcionales**, nunca como único mecanismo.
- **Condiciones de la mesa**:
  - Ni indicador de «pendiente de respuesta» al alumno ni contador de respuestas debidas al entrenador — con cualquiera de los dos, el panel de excepciones se convierte en bandeja de entrada (entrenador, **innegociable**).
  - Las frases enlatadas como único mecanismo están descartadas: *«recibir "descansa y lo vemos" por tercera vez me confirma que hablo con un formulario»* (alumno).
  - Visible solo para ese alumno y ese entrenador; sin indicación sanitaria ante molestias — «hablamos» sí, «descansa» no (admin).
  - El atajo de precargar un `override` con copia de la sesión base está **descartado por los cuatro roles**.
- **Coste y colisión**: el canal ya funciona de punta a punta (`PersonalizacionAplicada` → `mensaje_al_alumno` → la card de «mi semana» ya lo pinta). Pero la versión limpia —mensaje sin sobrescribir la sesión— choca con **[ADR-0002 D9](adr/0002-modelo-de-datos.md)**, que modela `Personalizacion` con `override: Sesion` **no opcional**. Requiere PR de cambio de ADR encadenada. Bajo con el ADR cambiado; el atajo que lo evitaba está vetado.
- **Límite conocido**: la alerta *«sin reportar > 7 días»* no trae día, así que no tiene sesión a la que colgar la respuesta. Cubre 3 de los 4 tipos de alerta.
- **Decisión pendiente**: si se paga la PR de cambio de ADR-0002 D9.
- **Recomendación**: sí. Es el cierre del bucle de feedback y el ADR es exactamente el mecanismo previsto para este caso.

### M-05 · Aviso por email cuando se publica el plan

- **Problema**: al publicar el plan no pasa nada. El entrenador acaba escribiendo «ya tenéis la semana» en el WhatsApp del grupo, que es justo lo que la herramienta venía a eliminar.
- **Evidencia**: [`journeys/coach-runner.md`](journeys/coach-runner.md) etapa 5 pide la notificación a los alumnos y el wireframe 05 ya la preveía en el modal de publicación. No existe: el email solo se usa en `identidad`.
- **Alcance mínimo**: al publicar, email corto a los alumnos del snapshot con enlace a `/mi-plan`. Si se republica la misma semana, no se reenvía.
- **Condiciones de la mesa**:
  - **Sin nombre de grupo ni contenido de la sesión**. Los grupos son texto libre del club y la taxonomía pre-cargada incluye *lesión* y *post-parto*: *«Ya tienes tu semana de Vuelta de lesión»* es un dato de salud en un buzón que el club no controla.
  - Sin casilla que marcar en cada publicación — si hay que decidirlo cada domingo, el entrenador lo veta.
  - Baja por tipo de aviso desde «mi cuenta».
- **Marco legal aplicable**: [ADR-0005 D12](adr/0005-email.md) prohíbe expresamente que un email transporte *«datos de salud (marcas, sesiones, reportes de entrenamiento, lesiones, observaciones médicas)»*. El aviso sin contenido pasa. Ojo al otro renglón de la misma sub-decisión: tampoco debe transportar *«información que el destinatario no haya solicitado explícitamente»* — es el argumento a favor del opt-in.
- **Coste**: medio, y no por el email. `EmailSender` vive en `identidad/application/ports/outbound/notification/`; enviar desde `planificacion` obliga a promocionar el puerto a `shared` o duplicarlo, o sea tocar fronteras de módulo ([ADR-0007](adr/0007-monolito-modular.md)). Postmark, plantillas y métricas ya están.
- **Decisión pendiente**: activado por defecto (postura del entrenador y de producto) frente a opt-in explícito (lo que sugiere la letra de ADR-0005 D12).
- **Recomendación**: por defecto, con baja de un clic en el propio email y en «mi cuenta», y el texto del consentimiento de alta mencionándolo. Es el único momento del ciclo semanal en que hoy el entrenador se ve obligado a salir del producto.

### M-06 · La alerta tiene estado (atendida)

- **Problema**: el panel se recalcula en cada visita. La misma alerta —«Ana lleva 9 días sin reportar»— reaparece cada mañana aunque ya se haya hablado con ella. El contador de cabecera no significa nada.
- **Evidencia**: el criterio de validación del wireframe 08 dice *«después de descartar, no aparece la misma alerta hasta que ocurra un nuevo evento»*, y su criterio de fallo es *«demasiado ruido»*. El hallazgo H-V4 llama a esa pantalla el corazón del producto.
- **Alcance mínimo**: acción «atendida» por tarjeta. Mientras está atendida, el sistema no la vuelve a levantar por el mismo hecho; sí por un hecho nuevo.
- **Condiciones de la mesa**: fuera el histórico de descartadas — pantalla nueva que nadie ha pedido (ingeniería) y archivo indefinido de quién tuvo molestias (admin). Persistir solo (tipo, alumno, entrenador, fecha), con `@RgpdCategory` y borrado en el `StudentDeletionListener` (admin).
- **Coste**: medio, no bajo. Verificado: el KDoc de `CoachAlert` dice que la alerta *«deja de listarse sola cuando deja de cumplirse su condición — no hay estado propio que persistir»*. Hay que dar identidad estable a algo que hoy se recalcula a demanda: tabla y escritura nuevas.
- **Decisión pendiente**: si entra también «posponer» (hasta el lunes / 7 días). **Conflicto abierto**: el entrenador lo quiere porque con 500 alumnos «atendida» sola no cubre *«ya he hablado con ella, revísalo el lunes»*; producto lo quiere fuera hasta que la beta demuestre que el problema existe con ~60 alumnos. Es decisión de negocio, no técnica.
- **Recomendación**: entra «atendida» y se mide el ruido en la beta antes de añadir «posponer».

### M-07 · Aviso neutro de consentimiento al entrenador

- **Problema**: quien ha revocado el consentimiento de datos de salud no puede reportar sesiones. El entrenador recibe una alerta de «sin reportar > 7 días» y persigue por WhatsApp a alguien que está legalmente bloqueado.
- **Evidencia**: el mecanismo de consentimiento ya está decidido y construido ([ADR-0014](adr/0014-rgpd.md)); `seguimiento` mantiene la proyección `consentimiento_alumno`. Lo que falta es la consecuencia funcional visible.
- **Alcance mínimo**: donde hoy saldría la alerta engañosa, un aviso neutro — «no comparte datos de salud» — sin motivo ni histórico.
- **Condiciones de la mesa**: nunca como chip permanente junto al nombre del alumno en el listado ni en su ficha (alumno). Fuera el contador y el filtro del admin (producto).
- **Coste**: bajo el aviso y el contador agregado. **El filtro en la lista de alumnos es lo caro**: esa lista la sirve `clubtaxonomia`, que no conoce el consentimiento — exigiría listener y proyección nuevos. Si se quiere, se sirve desde `seguimiento`.
- **Decisión pendiente**: ninguna relevante. Confirmar que el contador agregado queda fuera del primer lote.
- **Recomendación**: entra solo el aviso neutro.

### M-08 · Continuidad de las personalizaciones entre semanas

- **Problema**: la personalización de un alumno que vuelve de lesión se pierde cada domingo. Rehacerla a mano durante las 4-6 semanas que dura la vuelta, por cada alumno en esa situación, es exactamente el trabajo repetitivo que el modelo plan-por-grupo prometía quitar.
- **Evidencia**: hipótesis V4 de [`wireframes/findings.md`](wireframes/findings.md) — *si el uso de personalización baja del 10 % de las sesiones, M12 será revisable*. Bajará si hay que rehacerla cada semana.
- **Alcance mínimo**: al **publicar**, el modal de confirmación sugiere reaplicar las personalizaciones de la semana anterior, preseleccionadas y con un check para quitar cada una.
- **Condiciones de la mesa**:
  - Caducidad máxima (4 semanas) o la lista de personalizados se fosiliza en la lista de lesionados del grupo (admin, producto).
  - Si se reaplica, **siempre con mensaje al alumno**: el alumno no ve ningún indicador de «personalizada para ti» (decisión explícita de la ronda 2 de wireframes), así que una personalización que sobrevive a la lesión lo deja semanas con carga recortada sin forma de detectarlo (alumno).
- **Coste**: medio, y el problema no es el esfuerzo sino el mapeo: `Personalizacion` apunta a un `sesionId` del plan viejo, que no existe en la semana nueva. Hay que anclar la sugerencia al **día de la semana** y ofrecerla solo si ese día tiene sesión en el plan nuevo.
- **Decisión pendiente**: cuántas semanas dura la sugerencia antes de caducar.
- **Recomendación**: entra con caducidad de 4 semanas y mensaje obligatorio.

### M-09 · Captura de marca al reportar una competición

- **Problema**: el diferenciador (M19, «un plan, ritmos por corredor») se calcula sobre una marca que solo se actualiza si el alumno entra a «mis marcas». Una marca de hace año y medio resuelve ritmos sobre una versión del alumno que ya no existe, y nadie puede detectarlo porque el entrenador no la ve (M20).
- **Alcance mínimo**: al reportar una sesión de tipo Competición, pregunta opcional y saltable para guardar el tiempo como marca. El tiempo va **solo** a sus marcas.
- **Condiciones de la mesa**: en un paso **posterior** a guardar el reporte, con el aviso «solo tú lo ves» — encadenarlo al reporte hace creíble que el tiempo viaja al entrenador, que es justo la promesa que M20 sostiene (admin, alumno). Ningún contador de marcas caducadas llega a la salud del club.
- **Coste**: bajo, y menor de lo propuesto. Verificado: `frontend/src/app/features/marcas/mark-freshness.ts` **ya calcula** la obsolescencia y la pantalla ya pinta «actualizada hace N meses». El delta real es solo la captura al reportar.
- **Decisión pendiente**: ninguna relevante.
- **Recomendación**: entra, recortada a la captura.

### M-10 · Rescate del alumno sin marca, dentro de la aplicación

- **Problema**: un alumno sin la marca de referencia recibe la sesión sin ritmo. Que encuentre el CTA que lo arregla quedó **sin validar** en la ronda 2 de wireframes (*«se asume descubrible»*). Si no lo encuentra, el diferenciador le entrega un plan con huecos y nadie en el club se entera.
- **Alcance mínimo**: «mis marcas» muestra cuántas sesiones vivas desbloquea cada distancia, y al guardar la marca «mi semana» confirma el efecto. Todo dentro de la aplicación.
- **Condiciones de la mesa**: **fuera el aviso por email**, que era la parte original de la propuesta. Es la línea roja del alumno — *«un correo que me persigue por mis marcas convierte el único dato que me prometieron privado en un recordatorio que me llega al buzón»* — y choca con ADR-0005 D12, que nombra las marcas en la prohibición. Debe respetarse un «no me lo recuerdes más».
- **Descartado explícitamente**: el recuento de alumnos sin marca para el entrenador al publicar. Ayudaría, pero filtra por la puerta de atrás lo que M20 le prohíbe ver, y con grupos de cinco sería casi nominativo.
- **Coste**: bajo. El dato ya está: `plan_resuelto_por_alumno.ritmo_falta_marca`.
- **Decisión pendiente**: ninguna relevante.
- **Recomendación**: entra la versión in-app.

### M-11 · Temporada de carreras: cuenta atrás y archivado

- **Problema**: [`vision.md`](vision.md) promete la vista agregada del catálogo de carreras (*«¿cuántos van a la MMM?»*) y que *«cuando la carrera pasa de fecha, el sistema avisa al admin para archivarla»*. Ninguna de las dos existe: la fecha y la distancia se teclean como metadata del valor de tag y **ninguna vista las lee después**.
- **Alcance mínimo**: en la salud del club, las carreras del tag `objetivo` ordenadas por fecha con días restantes y número de alumnos apuntados; las pasadas, agrupadas con acceso directo a archivar, reutilizando el diálogo de impacto que ya existe.
- **Condiciones de la mesa**: producto observa que el agregado ya se obtiene filtrando por tag en la lista de alumnos (M3b); lo que justifica la mejora es la cuenta atrás y el archivado, no el recuento.
- **Coste**: bajo. Lectura de metadata ya introducida, en una pantalla que el admin ya visita.
- **Decisión pendiente**: sube a SHOULD si el club piloto arranca la beta arrastrando el catálogo de una temporada anterior; si arranca limpio, no hay carreras pasadas que archivar en tres meses.
- **Recomendación**: COULD, salvo que el piloto arrastre catálogo.

### M-12 · Lenguaje llano desacoplado del tag `nivel`

- **Problema**: la única palanca de lenguaje prevista (riesgo R19, y el SHOULD «adaptación de lenguaje por nivel») cuelga del tag `nivel`, que además decide en qué grupo cae el alumno y qué plan recibe. No se puede pedir una explicación más llana sin ser reclasificado a otro grupo y otro plan.
- **Evidencia**: hallazgo H-V1 de la validación de wireframes; AM, alumna de iniciación — *«Fartlek o RPE 6 me suena a chino»*.
- **Alcance recortado (lo que recomienda ingeniería)**: **no** hay texto llano que enseñar — `sesion` solo trae `notas` escritas por el entrenador en su jerga, así que un «modo llano» solo puede reordenar campos y añadir copy estático. Empezar por la explicación por tipo de sesión, sin dato nuevo y sin preferencia persistida, y construir el ajuste en «mi cuenta» solo si eso demuestra valor.
- **Condiciones de la mesa**: acotarlo a la card «hoy», que es donde AM se bloqueó; el cohorte comprometido para la beta es fondo avanzado (producto). La traducción es de forma, nunca de contenido: el ritmo exacto debe seguir a un toque (alumno).
- **Coste**: bajo la versión recortada, medio la completa.
- **Decisión pendiente**: hacer la versión recortada o nada hasta que haya alumnos de iniciación en la beta.
- **Recomendación**: la versión recortada, porque el desacople del tag `nivel` es correcto y barato; la preferencia persistida, después.

### M-13 · Registro de accesos del club

- **Problema**: el módulo de auditoría ya recoge denegaciones y accesos a datos sensibles de todos los módulos, y la API los expone solo al admin, pero **ninguna pantalla los usa**. Cuando una alumna pregunta quién ha visto que reportó molestias, la respuesta depende del equipo técnico y de una consulta a la base de datos.
- **Alcance mínimo**: pantalla de solo lectura en `/club`, filtrable por persona (por nombre, no por identificador), tipo y fechas.
- **Condiciones de la mesa**:
  - **La paginación va antes que la pantalla**: verificado, `AuditEventRepositoryImpl.kt:97` fija `SEARCH_LIMIT = 500` sin paginación. A volumen del club piloto esa ventana se agota en días y la pantalla mentiría por omisión.
  - Fuera el atajo «qué ha consultado este entrenador» desde su ficha: *«si mirar la ficha de un alumno deja rastro que alguien revisa, dejaré de mirarla»* (entrenador).
- **Objeción de producto (descarte)**: no toca ninguno de los cinco objetivos de `vision.md` y su frecuencia de uso en tres meses de beta es cercana a cero; con un club y un equipo de cuatro, una queja se responde con una consulta puntual. Disparador para reabrir: más de 3 quejas de acceso al mes durante 2 meses seguidos, o segundo club.
- **Coste**: bajo la pantalla, más la paginación como prerrequisito.
- **Decisión pendiente**: si la capacidad de responder «quién vio mis datos» es un prerrequisito de la beta con datos reales o se acepta resolverlo fuera de la aplicación.
- **Recomendación**: COULD, con la paginación hecha igualmente porque el límite de 500 sin paginar es un problema con o sin pantalla.

### M-14 · El alumno elige el valor de su tag `objetivo`

- **Problema**: la necesidad #2 de la persona de Marta es indicar a qué carrera apunta, eligiéndola del catálogo. Hoy los tags solo los asignan el admin o el entrenador (M5, M9): apuntarse a una carrera exige un WhatsApp y que alguien lo teclee.
- **Alcance mínimo**: en «mi cuenta», el alumno cambia su valor del tag `objetivo` eligiendo una carrera **vigente** del catálogo o «sin objetivo». Solo esa clave, fijada en código.
- **Objeciones**:
  - Producto la descarta: uno o dos cambios por alumno y temporada, sustituible por WhatsApp durante 6 meses, y a cambio alimenta el riesgo R16 (micro-grupos), sobre el que se sostiene el modelo de grupos. Disparador para reabrir: más de 10 cambios de `objetivo` al mes a petición de alumnos.
  - Admin: cambiar el objetivo mueve la pertenencia a grupos vivos, o sea **cambia quién ve sus reportes** sin que nadie del club se entere. El cambio debe quedar listado para revisión.
  - Entrenador: publico el domingo a un grupo distinto del que creía; condición mínima, que el modal de publicar diga quién ha entrado o salido desde la última publicación.
  - Ingeniería: el RBAC no basta — hace falta autorización a nivel de objeto y acotar a una sola `TagKey`, que hoy no es un concepto del permiso. No reabre la matriz configurable de ADR-0009 D6.
- **Coste**: medio.
- **Decisión pendiente**: aplazar con el disparador de producto o entrar con las condiciones del admin y del entrenador.
- **Recomendación**: aplazar con disparador. Los planes ya publicados están protegidos por el snapshot, pero el efecto sobre la membresía viva es el riesgo que menos conviene mover justo antes de la beta.

### M-15 · Excepciones manuales de pertenencia a la vista

- **Problema**: las excepciones manuales de M7 no caducan y nadie las lista; sobreviven al alumno que dejó de serlo.
- **Corrección verificada**: el caso «alguien que ya no existe» **no ocurre** — `PersonErasureJdbc` borra `club_taxonomia.grupo_alumno_override` al suprimir a la persona vía `StudentDeletionListener`. Queda solo el caso del alumno que dejó de serlo sin ser suprimido.
- **Objeción de producto (descarte)**: los residuos que limpia no existen todavía; un club que arranca no tiene excepciones huérfanas acumuladas. Es un problema del mes 12 y la beta se juega en el 3.
- **Condición del entrenador**: quitar una excepción viva le desmonta un ajuste suyo y su siguiente plan deja de llegar a ese alumno sin que nadie le avise.
- **Coste**: bajo; hoy no existe ninguna consulta que las liste.
- **Decisión pendiente**: aplazar al cierre de la primera temporada completa.
- **Recomendación**: aplazar.

---

## Descartadas en el contraste, con motivo

| Propuesta | Descartada por | Motivo |
|---|---|---|
| Resumen semanal por email al entrenador con sus alertas abiertas | Admin e ingeniería (línea roja de ambos) | Una alerta es «molestias reportadas» o «lesión declarada». ADR-0005 D12 prohíbe expresamente el dato de salud en email, y la sub-decisión existe literalmente para frenar este caso. Vaciada de nombres y tipos queda en «tienes N avisos», que el entrenador rechaza por ser un sitio más donde mirar que además miente en cuanto atiende el primero |
| Resumen mensual de salud del club por email al admin | Todos por omisión | Ningún rol lo defendió, ni el propio admin: el dato ya vive en una pantalla que visita una vez al mes |
| Empujón por email al alumno sin marca | Alumno (línea roja), admin, ingeniería | Rompe la promesa de privacidad de M20, sobre la que se sostiene el diferenciador, y ADR-0005 D12 nombra las marcas en la prohibición. La mejora sobrevive entera por la vía in-app (M-10) |
| Respuestas enlatadas como mecanismo único | Alumno | *«Recibir "descansa y lo vemos" palabra por palabra por tercera vez me confirma que hablo con un formulario»*: desmotiva más que el silencio actual. Sobrevive como plantilla opcional dentro de M-04 |
| Acuse «tu entrenador lo ha visto», sin contenido | Producto y entrenador (línea roja de ambos) | Certifica el silencio con fecha justo cuando el alumno decide si sigue reportando, y convierte el panel en un registro público de a quién no se ha atendido. Sobrevive solo como efecto derivado de responder (M-04) |
| Personalización «vacía» con el override precargado | Los cuatro roles | Ensucia la métrica V4 que decide si M12 se sostiene; miente al entrenador sobre qué sesiones ha personalizado; convierte el registro de personalizaciones en un proxy de quién está tocado; y combinado con M-08 congela el plan del alumno sin que lo vea |
| Histórico de alertas descartadas | Ingeniería, admin | Pantalla nueva que nadie ha pedido, y archivo indefinido de quién tuvo molestias |
| Contador y filtro de consentimiento para el admin | Producto, ingeniería | Reporting que no cambia ninguna conducta durante la beta, y el filtro obliga a listener y proyección nuevos en `clubtaxonomia` |
| Alerta al entrenador por sesión en conflicto | Entrenador, producto | El valor del panel es enseñar solo lo accionable y esto no lo es; basta con que la sesión descartada deje de dispararle un falso positivo |

## Aplazamientos y colisiones con decisiones ya tomadas

### Ninguna candidata reabre un aplazamiento de ADR-0015

| Candidata | Aplazamiento que roza | Disparador | ¿Cumplido? |
|---|---|---|---|
| M-05 | App móvil nativa (`vision.md` + ADR-0001), que es la vía de las notificaciones push | Demanda real validada en discovery | **No**. Por eso es email sobre Postmark, ya en producción, y no push. Las push activarían además §A1 (versionado de la API) |
| M-02, M-14 | Matriz de autorización configurable (ADR-0009 D6) | Cliente que pide un rol fuera de admin/entrenador/alumno, o segundo club piloto | **No**. Ambas son alcance fijo por relación (ADR-0009 D3) o un permiso fijado en código, no una matriz que el club configure |
| M-13 | Self-service de export RGPD (ADR-0014 D12); rol de soporte interno (ADR-0009 D19) | Segundo club o > 5 solicitudes/mes durante 2 meses; segundo club o incidencia sin admin disponible | **No**. Es consulta en pantalla del admin del club, sin exportación ni actor externo |

### Colisión con un ADR aceptado

**M-04 en su versión limpia** choca con **ADR-0002 D9**: `Personalizacion` modela `override: Sesion` como no opcional, y la persistencia declara `override JSONB` junto a `mensaje_al_alumno TEXT`. Un mensaje sin sobrescribir la sesión exige PR de cambio del ADR encadenada. La mesa descartó por unanimidad el atajo que la evitaba.

### Dos avisos sobre el índice maestro

- **M15 del backlog** (vista de cumplimiento explícita del entrenador por grupo, [ADR-0015 §A4](adr/0015-temas-aplazados-fuera-del-mvp.md)) no la propuso ningún rol, y es **barata de verdad**: `plan_resuelto_por_alumno` ya guarda `grupo_id` y la proyección `grupo_entrenador` ya existe. Su disparador —*el club piloto lo pide tras usar la beta*— **no está cumplido porque la beta no ha empezado**. Es la primera candidata a reabrirse en cuanto arranque.
- **Hueco no indexado**: el aplazamiento que resuelve M-03 vive solo en el KDoc de `ResolvedPlanReader` y en el README de `seguimiento`. No está en ADR-0015 ni en el backlog, pese a ser exactamente el tipo de no-decisión consciente que ese ADR existe para registrar.

## Qué hay que decidir

1. **M-02**: forma *(a)* o *(b)*, y si entra antes de la beta. Es lo único de este documento que no es opcional.
2. **M-01**: confirmar que entra, y si el «mover sesión de día» va en el mismo lote.
3. **M-03**: entra la versión mínima o se aplaza. En ambos casos, indexar el aplazamiento en ADR-0015.
4. **M-04**: se paga o no la PR de cambio de ADR-0002 D9.
5. **M-05**: aviso por defecto o opt-in.
6. **M-06**: entra «posponer» o solo «atendida» (conflicto entrenador ↔ producto).
7. **M-08**: cuántas semanas dura la sugerencia de reaplicar.
8. **M-13**: la trazabilidad de accesos es prerrequisito de la beta con datos reales, o no.
9. **M-14**: aplazar con disparador o entrar con condiciones.

> **Nota de mantenimiento, ajena a estas nueve decisiones**: `plan-implementacion-mvp.md` y `CLAUDE.md` siguen describiendo el proyecto como H0 con `seguimiento` en scaffold. No es una candidata ni una decisión de producto, pero mientras no se corrija cualquier estimación futura partirá de un estado falso — de hecho estuvo a punto de viciar esta misma mesa.

## Procedencia

- **Fuentes de negocio**: [`vision.md`](vision.md), [`glosario.md`](glosario.md), [`backlog.md`](backlog.md), [`personas/`](personas/), [`journeys/`](journeys/), [`research/findings.md`](research/findings.md), [`wireframes/findings.md`](wireframes/findings.md).
- **Fuentes de arquitectura**: ADR-0002 (D4, D9), ADR-0005 (D12), ADR-0007, ADR-0009 (D3, D6, D19), ADR-0014, ADR-0015 (§A4 y tabla maestra).
- **Afirmaciones verificadas en código** para este documento: `ListStudentsQuery.kt:23`, `UpdateSessionCommand.kt:61`, `ResolvedPlanReaderJdbc.kt:211`, `CoachAlert.kt`, `AuditEventRepositoryImpl.kt:97`, `mark-freshness.ts`, `ritmo_falta_marca` en las migraciones de `seguimiento`, `PersonErasureJdbc`.
- **Método**: tres rondas (propuesta independiente por rol, contraste cruzado con veredicto de vocabulario cerrado, síntesis). Veinte propuestas iniciales, quince supervivientes, nueve descartes con motivo.
