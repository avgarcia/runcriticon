# ADR-0016 — Runtime del backend: GraalVM (JIT vs imagen nativa)

- **Estado**: Propuesto
- **Fecha**: 2026-05-27
- **Decisores**: Arquitectura · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack, D2 — Kotlin), ADR-0006 (infraestructura, App Runner), ADR-0007 (monolito modular, Spring Modulith), ADR-0010 (CI/CD), ADR-0011 (observabilidad)

## Contexto y problema

ADR-0001 (D2) fija **Kotlin sobre Spring Boot 3** como backend. Falta decidir **sobre qué runtime corre** ese backend en producción. Hoy la elección por defecto sería OpenJDK / Temurin (HotSpot). En 2026 hay dos alternativas reales con GraalVM, muy distintas entre sí:

- **GraalVM como JDK** (modo JIT, sigue habiendo *bytecode* en runtime, mejor compilador interno).
- **GraalVM `native-image`** (AOT, binario nativo sin JVM, arranque en milisegundos).

Confundirlas lleva a decisiones equivocadas: una es casi un cambio de versión de JDK; la otra cambia el modelo de despliegue, debugging y CI. Este ADR las separa, las evalúa y deja la elección explícita.

La decisión es **independiente** del lenguaje (D2 sigue siendo Kotlin sin cambios) y del framework (Spring Boot soporta ambos modos vía Spring AOT desde la línea 3.x).

## Requisitos no funcionales relevantes

Los NFRs aplicables vienen de ADR-0001 y ADR-0006:

| Dimensión | Valor | Implicación para esta decisión |
|---|---|---|
| **Carga** | < 100 concurrentes en MVP | No exige optimización extrema de rendimiento |
| **Latencia** | p95 API < 400 ms | Alcanzable en JIT sin esfuerzo; nativa no aporta aquí |
| **Cold start** | App Runner puede *scale-to-zero* o mantener mínimo 1 instancia | **Sí relevante**: si la app duerme, el primer request paga el arranque |
| **Memoria** | App Runner factura por GB-hora | **Relevante a medio plazo**: imagen nativa consume 3-5× menos memoria |
| **Build time CI** | No hay objetivo formal, pero el equipo lo nota | Imagen nativa multiplica el tiempo de build x10-20 |
| **Disponibilidad** | Best-effort ~99% en MVP | No exige rapidez de scale-out extrema |

## Drivers de la decisión

- **Coste de despliegue en App Runner**: la memoria de la instancia afecta directamente a la factura. Una imagen nativa permite instancias más pequeñas.
- **Cold start aceptable**: si App Runner se configura con *scale-to-zero* o si el número de instancias mínimas se reduce a 0 fuera de horario, el cold start importa. Si siempre hay 1 instancia caliente, importa muy poco.
- **Complejidad para el equipo**: el equipo es interno y no ha trabajado todavía con imagen nativa en producción. Cada nivel de complejidad técnica nueva añade riesgo en H0 y H1.
- **Reversibilidad**: una decisión que se puede revertir trivialmente en una semana pesa menos que una que requiere reescribir partes del backend.
- **Ecosistema Spring Modulith**: ADR-0007 fija Spring Modulith, que **es compatible** con imagen nativa pero requiere validación específica de los eventos y la introspección de módulos.
- **Velocidad de iteración en H0/H1**: el coste de tiempo de CI es real; un build nativo de 10 minutos por commit ralentiza la cadencia de demos quincenales.

## Opciones consideradas

- **Opción A** — GraalVM como JDK en modo JIT.
- **Opción B** — Imagen nativa con GraalVM `native-image` + Spring AOT.
- **Opción C** — OpenJDK / Temurin (sin GraalVM). *Statu quo* implícito; se incluye para tener referencia.

### Opción A — GraalVM JDK (modo JIT)

Sustituir OpenJDK por GraalVM como JDK del proyecto. El código sigue compilándose a *bytecode*; en runtime hay JIT, pero el compilador es **Graal** en lugar de C2 de HotSpot. No hay cambio en frameworks, en librerías ni en el modelo de despliegue: la app sigue siendo un `jar` ejecutable.

- 👍 **Plug & play**: cambiar la imagen base del `Dockerfile` y la distribución del JDK en el pipeline. Resto del proyecto sin tocar.
- 👍 Compilador Graal suele dar **5-15 % más de rendimiento** en cargas típicas Java/Kotlin frente a C2 — beneficio modesto pero gratuito.
- 👍 **Totalmente reversible**: si no convence, se vuelve a OpenJDK con un cambio de una línea en el `Dockerfile`. Cero deuda técnica.
- 👍 GraalVM CE (Community Edition) es gratuito y suficiente.
- 👍 Mantiene el modelo mental del equipo — siguen siendo *jars*, debug habitual, profilers habituales.
- 👎 No reduce cold start (sigue habiendo arranque JVM).
- 👎 No reduce memoria (sigue habiendo JVM con su *footprint*).
- 👎 Beneficio medible en *throughput*/latencia es modesto en cargas no CPU-intensivas como la nuestra.
- 👎 Compromiso pequeño con un JDK menos común — pool de gente que lo conoce a fondo es menor que el de Temurin.

### Opción B — Imagen nativa (GraalVM `native-image` + Spring AOT)

Compilar el backend a un **binario nativo** vía Spring AOT + `native-image`. Sin JVM en producción: el binario contiene todo lo que necesita para ejecutar la aplicación.

- 👍 **Arranque en milisegundos** (vs 2-5 s de la JVM caliente). Cold start deja de ser un problema; *scale-to-zero* se vuelve viable sin penalización.
- 👍 **Memoria 3-5× menor**: típicamente 100-150 MB residentes en producción frente a 400-700 MB de Spring Boot en JVM caliente. Instancias más pequeñas en App Runner → factura menor.
- 👍 Despliegues más rápidos (binario más ligero que un jar + JVM).
- 👍 Sin runtime que actualizar por separado: el binario lleva todo dentro.
- 👎 **Reflexión y proxies dinámicos limitados**. Spring AOT cubre Spring; cualquier librería externa que use reflexión sin *hints* puede fallar en runtime. Cada dependencia nueva exige verificar compatibilidad nativa.
- 👎 **Spring Modulith (ADR-0007) es compatible pero requiere validación**: los listeners de eventos `@ApplicationModuleListener`, el outbox de eventos y la introspección de módulos para tests funcionan, pero hay que probarlo en pipeline y mantener los *hints* AOT que Spring auto-genera.
- 👎 **Build muy lento**: una imagen nativa tarda **3-10 minutos** en compilarse, vs **30 segundos** del `jar`. El CI se alarga; el ciclo *commit → demo* sufre.
- 👎 **Debugging más complejo**: nada de *hot reload*, JFR/profilers tradicionales no aplican directamente, hay que aprender herramientas específicas (`native-image-agent`, Native Image Inspect).
- 👎 **Tests duales obligatorios**: en local el equipo seguirá iterando con JVM (no es viable iterar con builds nativos de 10 min). CI tendrá que ejecutar la suite contra **ambos**: JVM (rápido, en cada PR) y nativo (al menos en nightly). Cualquier incompatibilidad que aparezca solo en nativo es coste.
- 👎 **Semi-irreversible**: revertir AOT requiere desmontar configuración de Spring AOT, *hints* y posibles parches en libs. No es un *git revert* de una línea.
- 👎 **Ecosistema más reciente en producción**. Aunque estable, Spring Native sigue siendo el camino menos transitado. Bugs raros tienen menos gente que los haya visto.

### Opción C — OpenJDK / Temurin (sin GraalVM)

Mantener el JDK estándar del ecosistema (Temurin/Eclipse Adoptium). Es el camino por defecto si este ADR no decide nada.

- 👍 **Lo más estándar**: el 90 % del ecosistema Spring corre así. Cualquier problema tiene respuesta inmediata en internet.
- 👍 Pool de gente que lo conoce máximo.
- 👍 Cero curva nueva para el equipo.
- 👎 No aprovecha ni el rendimiento JIT de GraalVM ni el cold-start/memoria de la imagen nativa.
- 👎 Memoria y cold start son los del Spring Boot estándar (~500 MB de pie, 2-5 s de arranque caliente).

## Decisión

**Opción A — GraalVM JIT como JDK del backend.** La Opción B (imagen nativa) se mantiene como palanca futura, condicionada a datos.

Razón principal: el coste/beneficio de B no se justifica con los NFRs de un mono-club de 550 usuarios. Los argumentos a favor de B —cold start, memoria— solo cobran peso si **se configura *scale-to-zero* en App Runner** o si **la factura de memoria se vuelve un problema observable**, y ninguno de los dos pasa en MVP. A cambio, B introduce *build time* x10, debugging complejo, tests duales y deuda técnica reversible solo con esfuerzo serio. El equipo es interno y empieza desde cero — apilar imagen nativa sobre Kotlin, Spring Modulith, hexagonal y events-first es demasiada novedad para H0/H1.

A es la apuesta razonable: aporta una mejora marginal de rendimiento (5-15 % en JIT con compilador Graal), es **reversible en un commit** (cambio de imagen base del `Dockerfile`) y prepara el terreno: si en el futuro decidimos pasar a B, ya estaremos sobre el mismo *vendor* de runtime.

Se descarta **C (OpenJDK puro)** solo a nivel narrativo. En la práctica, las diferencias operativas entre A y C son **mínimas** (mismo *jar*, mismo Spring, mismo debugging); A simplemente cambia el binario del JDK. Si en algún momento GraalVM CE planteara fricción operativa que no anticipamos, retroceder a C es una línea de `Dockerfile`.

### Condiciones para reabrir la decisión hacia B

La promoción de A → B se activa **solo con datos de la beta**, no por anticipación. Disparadores concretos:

- App Runner factura > 1.5× lo previsto por consumo de memoria → evaluar si reducir el *footprint* compensa el coste de adoptar imagen nativa.
- Se decide configurar *scale-to-zero* en App Runner (por ejemplo, por horas valle muy marcadas) y el cold start observado de la JVM (~3-5 s) penaliza UX.
- Spring Native madura un paso más y librerías clave que hoy son cuestionables (p. ej. drivers JDBC complejos) muestran soporte AOT robusto.

Sin alguno de esos disparadores, esta decisión no se reabre.

## Consecuencias

### Positivas

- **Cambio de runtime indoloro**: el equipo cambia OpenJDK por GraalVM CE sin tocar código, sin tocar tests, sin tocar pipeline. Single point of change: el `Dockerfile` y posiblemente la *toolchain* de Gradle.
- **Mejora marginal de rendimiento** (5-15 %) gratis en JIT cargas no críticas.
- **Mismo modelo mental** que el equipo viene esperando — *jars*, JVM, debugging y profiling habituales.
- **Reversibilidad total**: si GraalVM CE introduce fricción inesperada, retroceder es una línea de configuración.
- **Puerta abierta** a la Opción B sin coste de migración añadido cuando los disparadores activen la decisión.

### Negativas / coste asumido

- **Beneficio cuantificable modesto** sobre OpenJDK estándar. Para 550 usuarios y carga baja, la diferencia será difícil de medir.
- **Compromiso explícito con un JDK menos común** que Temurin/Adoptium. Soporte de la comunidad ligeramente menor (pero ya muy maduro a estas alturas).

### Riesgos y mitigaciones

- **Algún plugin de Gradle o herramienta de build tenga problema con GraalVM CE en lugar de Temurin** → mitigación: validar el build completo en CI sobre GraalVM CE antes de cerrar H0. Si aparece un problema bloqueante, retroceder a C (Temurin) con cambio de una línea.
- **El equipo asume "estoy usando GraalVM, debería tener imagen nativa"** → mitigación: este ADR es explícito sobre que A no es B. Cualquier intento de saltar a B requiere reabrir este ADR con los disparadores cumplidos.
- **Una nueva LTS de Java sale antes que la versión soportada por GraalVM CE** → mitigación: la política de actualización (ADR-0001, D12) ya prevé revisión por LTS; añadimos en el *checklist* de esa revisión "¿está GraalVM CE en esta LTS aún?". Si no, se valora retroceder a C temporalmente.

## Notas

- Versión de GraalVM CE: la vigente sobre Java 21 LTS al iniciar el desarrollo (alineada con ADR-0001, D12). Se actualiza siguiendo la misma política que el resto del JDK.
- **GraalVM Enterprise** (con compilador propietario, hasta hace poco bajo licencia comercial) no entra en consideración: GraalVM CE es suficiente para nuestros NFRs y evita un proveedor comercial. Si Oracle cambiara el modelo de licencia y CE perdiera viabilidad, retroceder a C es la salida limpia.
- Este ADR **no fija** la configuración de App Runner (scale-to-zero, número de instancias mínimas, tamaño de instancia) — eso vive en ADR-0006. Aquí solo se acota qué runtime usamos.
- **Confirmación**: Antonio (decisor) confirma la Opción A el 2026-05-27. La promoción a B queda condicionada a los disparadores listados en *Decisión*, no a anticipación.
