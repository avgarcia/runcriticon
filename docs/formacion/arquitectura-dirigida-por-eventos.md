# Plan de formación — Arquitectura dirigida por eventos (events-first)

Objetivo: aplicar **correctamente** el modelo de comunicación *events-first* entre los módulos de Runcriticon (ADR-0007) — eventos de dominio, proyecciones locales y consistencia eventual.

> Recurso transversal: la documentación de **Spring Modulith**, y la literatura de **Domain-Driven Design** sobre eventos de dominio.

---

## Nivel 0 — Fundamentos

**Objetivo:** entender por qué un monolito modular puede comunicarse por eventos.

- Monolito modular: un solo desplegable, módulos con fronteras explícitas (ADR-0007).
- **Síncrono vs asíncrono**: llamar y esperar respuesta, frente a notificar y seguir.
- Qué significa *events-first*: los módulos **no se llaman entre sí**; se comunican publicando y consumiendo **eventos**.
- El intercambio: máxima **autonomía** de cada módulo a cambio de **consistencia eventual**.

**Conexión Runcriticon:** es la decisión del ADR-0007. Leerlo antes de seguir.

---

## Nivel 1 — Eventos de dominio

**Objetivo:** saber qué es un evento y cómo se diseña.

- **Evento de dominio**: un hecho relevante que **ya ha ocurrido** en un módulo.
- Se nombran en **pasado**: `AlumnoAsignadoAGrupo`, `PlanPublicado`, `UsuarioInvitado`.
- **Evento ≠ comando**: un comando pide que algo ocurra; un evento informa de que ya ocurrió.
- Qué datos lleva un evento: lo necesario para que los consumidores reaccionen, sin acoplarse al interior del productor.

**Conexión Runcriticon:** cada cambio relevante en un módulo (un alumno cambia de grupo, se publica un plan) será un evento.

---

## Nivel 2 — Publicar y consumir eventos

**Objetivo:** mover eventos de un módulo a otro de forma fiable.

- Modelo **publicar / suscribir**: el productor no conoce a sus consumidores.
- El **registro de publicación de eventos de Spring Modulith** y cómo se usa.
- El **patrón *outbox***: el evento se persiste **en la misma transacción** que el cambio de estado → si el cambio se confirma, el evento **se entregará** (entrega *al menos una vez*), aunque el sistema se reinicie.
- Por qué la doble escritura (cambiar estado + publicar a la vez en dos sistemas) es un problema, y cómo el *outbox* lo resuelve.

**Conexión Runcriticon:** es el mecanismo del ADR-0005 (email por *outbox*) y de ADR-0007 en general.

---

## Nivel 3 — Proyecciones / read models locales

**Objetivo:** que un módulo tenga los datos de otros sin preguntarles.

- Una **proyección local**: la copia que un módulo mantiene de los datos de otro contexto que necesita.
- Se construye y se actualiza **consumiendo eventos** del módulo de origen.
- Un módulo **nunca consulta** a otro: lee su propia proyección.
- Qué poner en una proyección: solo lo que ese módulo necesita, con su forma — no una copia entera del otro módulo.

**Conexión Runcriticon:** la vista de salud del club (ADR-0004) y los datos de relación que usa la autorización (ADR-0009) son proyecciones locales.

---

## Nivel 4 — Consistencia eventual

**Objetivo:** convivir con que los datos "se ponen al día poco después".

- Qué es la **consistencia eventual**: tras un cambio, hay una **ventana** hasta que las proyecciones lo reflejan.
- Cuándo es **aceptable** (la inmensa mayoría de las vistas y comprobaciones) y cuándo **no** (operaciones que exigen el dato exacto al instante).
- Cómo razonar y comunicar esa ventana; cómo diseñar para que no moleste al usuario.

**Conexión Runcriticon:** ADR-0009 acepta una ventana breve en los datos de relación de la autorización; ADR-0007 asume la consistencia eventual como norma entre módulos.

---

## Nivel 5 — Idempotencia y fiabilidad

**Objetivo:** que procesar un evento dos veces no rompa nada.

- **Entrega al menos una vez**: un evento **puede llegar repetido** — hay que contar con ello.
- **Consumidor idempotente**: procesar el mismo evento dos veces produce el mismo resultado que procesarlo una.
- Técnicas: registrar los eventos ya procesados, operaciones naturalmente idempotentes.
- **Orden** de los eventos: qué garantías hay y cómo no depender de un orden que no existe.

**Conexión Runcriticon:** ADR-0007 exige consumidores idempotentes.

---

## Nivel 6 — Versionado y evolución de eventos

**Objetivo:** cambiar un evento sin romper a quien lo consume.

- Un evento es un **contrato** entre el productor y sus consumidores.
- Cambios **compatibles** (añadir un campo opcional) vs **incompatibles** (quitar o renombrar).
- Estrategias para evolucionar el esquema de un evento; versionar eventos.

**Conexión Runcriticon:** los eventos viven mientras viva el producto — conviene tratarlos como API.

---

## Nivel 7 — Testing de sistemas dirigidos por eventos

**Objetivo:** probar productores, consumidores y proyecciones.

- Probar que un caso de uso **publica** el evento esperado.
- Probar que un consumidor **reacciona** correctamente, incluida la **idempotencia** (entregarle el evento dos veces).
- Probar que una **proyección** queda en el estado correcto tras una secuencia de eventos.
- Las pruebas de **fronteras de Spring Modulith** (ADR-0010).

**Conexión Runcriticon:** encaja en la estrategia de tests del ADR-0010.

---

## Nivel 8 — Errores comunes a evitar

**Objetivo:** reconocer los antipatrones.

- **Eventos anémicos o demasiado gordos**: ni tan vacíos que el consumidor tenga que preguntar, ni una copia entera del estado del productor.
- **Acoplamiento por eventos**: si el productor tiene que saber quién consume, el desacoplamiento es falso.
- **Proyecciones que divergen**: una proyección mal mantenida que deja de reflejar la realidad.
- Usar eventos para lo que pide **respuesta inmediata** — la consistencia eventual tiene un coste; aplicarla donde de verdad aporta.
- *Sagas* (procesos de varios pasos coordinados por eventos) mal diseñadas.

**Conexión Runcriticon:** la revisión del ADR-0007 sopesó justamente estos costes.

---

## Práctica recomendada

En un proyecto de pruebas con Spring Modulith: dos módulos donde uno publica un evento, el otro lo consume y mantiene una proyección local; añadir un test de idempotencia entregando el evento dos veces.

## Recursos de partida

- Documentación de **Spring Modulith** (módulos, eventos, *event publication registry*, testing).
- Literatura de **Domain-Driven Design** sobre **eventos de dominio** (Eric Evans; *Implementing Domain-Driven Design*, Vaughn Vernon).
- El **patrón *transactional outbox*** y el **patrón saga** — material de referencia sobre arquitecturas dirigidas por eventos.
