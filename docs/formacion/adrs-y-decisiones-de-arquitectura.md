# Plan de formación — ADRs y toma de decisiones de arquitectura

Objetivo: que cualquiera del equipo —negocio incluido— sepa **tomar, razonar y documentar** decisiones de arquitectura con criterio, usando los ADR de Runcriticon como caso de estudio vivo.

> Recurso transversal e irreemplazable: **los propios ADR de `docs/adr/`**. Están escritos como ejemplo de buen ADR; leerlos es la mitad de esta formación.

---

## Módulo 1 — Qué es una decisión de arquitectura y por qué documentarla

**Objetivo:** entender qué problema resuelve un ADR.

- Qué es una decisión de arquitectura: una elección **cara de revertir** que condiciona el resto.
- Por qué se documenta: evita repetir debates, explica el *porqué* a quien se incorpora, deja rastro.
- Coste de **no** documentar: decisiones "fantasma" que nadie recuerda ni puede cuestionar.

**Conexión Runcriticon:** leer `docs/adr/README.md` — el "por qué ADRs" del proyecto.

---

## Módulo 2 — El formato ADR y MADR

**Objetivo:** conocer la estructura.

- Qué es un **ADR** (Architecture Decision Record) y el formato **MADR** (Markdown Any Decision Records).
- Las secciones: contexto, drivers, opciones, decisión, consecuencias, notas.
- Un ADR = **una** decisión. Ficheros numerados, en el repo, revisados por PR.

**Conexión Runcriticon:** comparar `docs/adr/template.md` con un ADR real.

---

## Módulo 3 — Anatomía de una buena decisión

**Objetivo:** saber qué hace sólido a un ADR.

- **Contexto y problema**: el escenario y las fuerzas en juego.
- **Drivers**: los criterios contra los que se mide cada opción.
- **Opciones con nombre**: siempre varias, descritas con honestidad — también las descartadas.
- **Trade-offs**: qué se gana y qué se pierde con la elegida.
- **Consecuencias**: lo que se vuelve fácil, lo que se vuelve difícil, lo que habrá que revisar.

**Conexión Runcriticon:** el ADR-0001 o el 0008 como modelos de este patrón.

---

## Módulo 4 — Identificar qué merece un ADR

**Objetivo:** no documentarlo todo, ni dejar sin documentar lo importante.

- Criterios: **significancia** (¿afecta a muchas partes?) y **coste de revertir**.
- Lo que normalmente **no** es un ADR: detalles de implementación reversibles en una tarde.
- Detectar la decisión **escondida** dentro de una tarea.

**Conexión Runcriticon:** el ADR-0009 nació de una pregunta durante una revisión — una decisión que estaba sin registrar.

---

## Módulo 5 — Evaluar trade-offs con criterio

**Objetivo:** comparar opciones sin autoengañarse.

- **Requisitos no funcionales**: latencia, coste, mantenimiento, conocimiento del equipo — pesan tanto como las funcionalidades.
- **Reversibilidad**: ¿es una puerta de una o de dos direcciones?
- Sesgos a vigilar: moda tecnológica, *"lo que ya conozco"*, sobrevalorar el futuro lejano.
- Cuándo una opción "peor sobre el papel" es la correcta por contexto.

**Conexión Runcriticon:** la revisión del ADR-0001 incorporó una tabla de requisitos no funcionales precisamente por esto.

---

## Módulo 6 — Estados y ciclo de vida

**Objetivo:** entender que un ADR vive en el tiempo.

- Estados: **Propuesto → Aceptado**; y **Reemplazado** / **Obsoleto**.
- **Inmutabilidad**: un ADR aceptado no se reescribe; si la decisión cambia, se crea uno nuevo que lo sustituye.
- Por qué esa inmutabilidad preserva la historia del *porqué*.

**Conexión Runcriticon:** la tabla de estados de `docs/adr/README.md`.

---

## Módulo 7 — Patrones de criterio arquitectónico

**Objetivo:** interiorizar heurísticas que se repiten en buenas decisiones.

- **"Diseña simple, evoluciona con señal"**: resolver el problema de hoy sin cerrarse el de mañana.
- **Proporcionar la decisión al problema**: ni sobreingeniería ni quedarse corto.
- Evitar la **optimización prematura** y los **microservicios/abstracciones prematuros**.
- Aislar tras una interfaz lo que de verdad puede cambiar.

**Conexión Runcriticon:** este patrón recorre casi todos los ADR (monolito modular, "hexagonal con criterio", SES como evolución de Postmark…).

---

## Módulo 8 — Herramientas y práctica

**Objetivo:** llevarlo al día a día.

- **log4brains** — generar un sitio navegable a partir de la carpeta `docs/adr/` (tarea pendiente del proyecto).
- ADRs versionados en el repo y revisados por PR, como el resto del código.
- Cómo **revisar** un ADR de otra persona: cuestionar drivers, buscar opciones que faltan, comprobar coherencia con otros ADR.

**Conexión Runcriticon:** la propia serie de revisiones de los ADR 0001-0009 es un ejemplo de revisión crítica — leerla como caso de estudio.

---

## Práctica recomendada

Leer los nueve ADR de Runcriticon en orden y, para cada uno, preguntarse: ¿cuáles eran los drivers?, ¿qué opción habría elegido yo?, ¿qué se aplazó y por qué? Después, redactar un ADR de práctica sobre una decisión pequeña usando `template.md`.

## Recursos de partida

- El artículo original de **Michael Nygard** sobre *Architecture Decision Records*.
- El sitio del proyecto **MADR** (Markdown Any Decision Records).
- La documentación de **log4brains**.
- Lectura de fondo sobre toma de decisiones y trade-offs: *Fundamentals of Software Architecture* (Richards & Ford).
