# ADR-0016 — Runtime del backend: GraalVM (JIT vs imagen nativa)

- **Estado**: Aceptado
- **Fecha**: 2026-05-27 · revisado 2026-05-30 (reorganización Nivel 1: premisas heredadas, NFRs propios complementarios, sub-decisiones numeradas D1-D11 con anchors; incorporación de: **versión específica** GraalVM CE 21.x con política de revisión semestral, **imagen base concreta** `ghcr.io/graalvm/jdk-community:21`, **setup en CI** con `actions/setup-java`, **GraalVM CE también en local** con toolchain Gradle, **checklist de validación pre-H0**, **disparador económico cuantitativo** cruzado con ADR-0006 D26, invariante anti-confusión GraalVM CE ≠ imagen nativa) · **aceptado 2026-05-30** · revisado 2026-06-03 (**runtime a GraalVM CE 25**; la compilación se mantiene en **target Java 21** porque detekt 1.23.7 / Kotlin 2.1.0 no soportan jvm-target 25 — checklist D8; no reabre A=JIT) · revisado 2026-06-12 (corrección de drift de la revisión 2026-06-03: títulos de D3/D4 en índice y headings alineados al runtime CE 25; aclarado en D6/D7/D8 y Consecuencias que las menciones a CE 21 refieren al JDK de build/test, no al runtime; sin cambio de decisión)
- **Decisores**: Arquitectura · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack, D2 — Kotlin, D12 — política LTS), ADR-0006 (infraestructura, App Runner sin scale-to-zero en MVP, coste objetivo), ADR-0007 (monolito modular, Spring Modulith), ADR-0010 (CI/CD, GitHub Actions, imagen Docker como artefacto frontera), ADR-0011 (observabilidad, JFR/profilers)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre el runtime del backend. Las once sub-decisiones se agrupan en seis áreas:

- **Elección de runtime (D1-D2)** — Opción A (JIT con GraalVM CE) y descarte explícito de native-image en MVP.
- **Versión y empaquetado (D3-D5)** — versión LTS, imagen base concreta, política de revisión.
- **CI/CD y local (D6-D7)** — setup en GitHub Actions, mismo JDK de build en local que en CI.
- **Validación y guardarrailes (D8)** — checklist de validación antes de cerrar H0.
- **Anti-confusión y descartes (D9-D10)** — invariante "GraalVM CE ≠ imagen nativa", GraalVM Enterprise fuera por licencia.
- **Disparadores hacia B (D11)** — promoción a imagen nativa solo con datos.

| #   | Sub-decisión                                                                       | Capa         |
|-----|------------------------------------------------------------------------------------|--------------|
| D1  | [GraalVM CE como JDK del backend (Opción A — JIT)](#d1)                            | Estratégica  |
| D2  | [Modo JIT explícitamente; no `native-image` en MVP](#d2)                           | Estratégica  |
| D3  | [Versión: runtime GraalVM CE 25 (Java 25 LTS), target de compilación Java 21](#d3) | Operativa    |
| D4  | [Imagen base: `ghcr.io/graalvm/jdk-community:25` (runtime; build stage `:21`)](#d4) | Operativa    |
| D5  | [Política de revisión: semestral + ante nueva minor con CVE](#d5)                  | Operativa    |
| D6  | [Setup en CI con `actions/setup-java` (`graalvm-community`)](#d6)                  | Operativa    |
| D7  | [GraalVM CE también en local con toolchain Gradle](#d7)                            | Operativa    |
| D8  | [Checklist de validación antes de cerrar H0](#d8)                                  | Operativa    |
| D9  | [Invariante anti-confusión: GraalVM CE ≠ imagen nativa](#d9)                       | Estratégica  |
| D10 | [GraalVM Enterprise descartado por licencia](#d10)                                 | Operativa    |
| D11 | [Disparadores cuantitativos para promover A → B](#d11)                             | Estratégica  |

## Contexto y problema

ADR-0001 (D2) fija **Kotlin sobre Spring Boot 3** como backend. Falta decidir **sobre qué runtime corre** ese backend en producción. Hoy la elección por defecto sería OpenJDK / Temurin (HotSpot). En 2026 hay dos alternativas reales con GraalVM, muy distintas entre sí:

- **GraalVM como JDK** (modo JIT, sigue habiendo *bytecode* en runtime, mejor compilador interno).
- **GraalVM `native-image`** (AOT, binario nativo sin JVM, arranque en milisegundos).

Confundirlas lleva a decisiones equivocadas: una es casi un cambio de versión de JDK; la otra cambia el modelo de despliegue, debugging y CI. Este ADR las separa, las evalúa y deja la elección explícita.

La decisión es **independiente** del lenguaje (D2 sigue siendo Kotlin sin cambios) y del framework (Spring Boot soporta ambos modos vía Spring AOT desde la línea 3.x).

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR**. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Kotlin sobre Spring Boot 3** (ADR-0001 D2). El lenguaje y el framework no cambian con esta decisión.
- **Java 25 LTS** como runtime (ADR-0001 D12; LTS vigente desde sep-2025). La compilación se mantiene en **target Java 21** hasta que detekt/Kotlin soporten 25. Fija la mayor de GraalVM CE.
- **App Runner como cómputo** con **`min=1` en MVP — sin scale-to-zero** (ADR-0006 D3, D4). Razón clave: el cold start de la JVM no es un problema porque siempre hay una instancia caliente.
- **Dimensionado App Runner 1 vCPU / 2 GB** (ADR-0006 D4). La memoria que la imagen nativa ahorraría no es restrictiva al volumen del piloto.
- **Coste objetivo MVP < 150 €/mes** con alarma crítica a 200 €/mes (ADR-0006 D26). Base para el disparador económico cuantitativo de D11.
- **Spring Modulith** (ADR-0007 D6). En **A (JIT) funciona sin trabajo extra**; en B (`native-image`) requeriría validación específica del outbox y los listeners.
- **CI/CD con GitHub Actions** (ADR-0010 D1) — `setup-java` soporta `distribution: graalvm-community`.
- **Imagen Docker como artefacto frontera entre CI y CD** (ADR-0010 D3). El runtime se empaqueta en la imagen.
- **Imagen Docker versionada por tag** (ADR-0010 D18). El tag incluye la versión del runtime.
- **NFR latencia p95 < 400 ms** (ADR-0001) — alcanzable con JIT sin esfuerzo extra.
- **Carga MVP < 100 concurrentes** (ADR-0001) — no exige optimización extrema.
- **Equipo de 4 personas** — apilar novedades sobre Kotlin + Spring Modulith + hexagonal + events-first es demasiado para H0/H1.

## Requisitos no funcionales

Los NFRs aplicables vienen de ADR-0001, ADR-0006 y este ADR:

| Dimensión | Valor | Implicación para esta decisión |
|---|---|---|
| **Carga** | < 100 concurrentes en MVP (ADR-0001) | No exige optimización extrema de rendimiento |
| **Latencia** | p95 API < 400 ms (ADR-0001) | Alcanzable en JIT sin esfuerzo; nativa no aporta aquí |
| **Cold start** | App Runner `min=1` en MVP — sin scale-to-zero (ADR-0006 D4) | **No relevante en MVP**: si se activase scale-to-zero, sí |
| **Memoria** | App Runner factura por GB-hora (ADR-0006 D4) | **Relevante a medio plazo**: imagen nativa consume 3-5× menos memoria |
| **Coste objetivo MVP** | < 150 €/mes con alarmas a 100 €/200 € (ADR-0006 D26) | Base del disparador económico de D11 |
| **Build time CI** | Sin objetivo formal; **aumento aceptable < 30 s o < 15 %** vs Temurin | Imagen nativa multiplica el tiempo de build x10-20 |
| **Tamaño imagen Docker** | **Aumento aceptable < 50 MB** vs Temurin | Cambio de JDK trae alguna penalización de tamaño |
| **Disponibilidad** | Best-effort ~99 % en MVP (ADR-0001) | No exige rapidez de scale-out extrema |

## Drivers de la decisión

- **Coste de despliegue en App Runner**: la memoria de la instancia afecta directamente a la factura. Una imagen nativa permite instancias más pequeñas — relevante **a medio plazo**, no en MVP.
- **Cold start aceptable**: App Runner está configurado con `min=1` (ADR-0006 D4) en MVP, sin scale-to-zero. El cold start de la JVM es irrelevante.
- **Complejidad para el equipo**: el equipo es interno y no ha trabajado todavía con imagen nativa en producción. Cada nivel de complejidad técnica nueva añade riesgo en H0 y H1.
- **Reversibilidad**: una decisión que se puede revertir trivialmente en un commit pesa menos que una que requiere reescribir partes del backend.
- **Ecosistema Spring Modulith**: ADR-0007 fija Spring Modulith, que **es compatible con A sin trabajo extra**. Con B requeriría validación específica de los eventos y la introspección de módulos (D9).
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
- 👎 **Semi-irreversible**: revertir AOT requiere desmontar configuración de Spring AOT, *hints* y posibles parches en libs. No es un `git revert` de una línea.
- 👎 **Ecosistema más reciente en producción**. Aunque estable, Spring Native sigue siendo el camino menos transitado. Bugs raros tienen menos gente que los haya visto.

### Opción C — OpenJDK / Temurin (sin GraalVM)

Mantener el JDK estándar del ecosistema (Temurin/Eclipse Adoptium). Es el camino por defecto si este ADR no decide nada.

- 👍 **Lo más estándar**: el 90 % del ecosistema Spring corre así. Cualquier problema tiene respuesta inmediata en internet.
- 👍 Pool de gente que lo conoce máximo.
- 👍 Cero curva nueva para el equipo.
- 👎 No aprovecha ni el rendimiento JIT de GraalVM ni el cold-start/memoria de la imagen nativa.
- 👎 Memoria y cold start son los del Spring Boot estándar (~500 MB de pie, 2-5 s de arranque caliente).

## Decisión

**Opción A — GraalVM CE como JDK del backend en modo JIT.** La Opción B (imagen nativa) se mantiene como palanca futura, condicionada a datos (D11). Las once sub-decisiones desarrolladas a continuación. Cinco son **estratégicas** (D1, D2, D9, D11 — runtime elegido, modo JIT explícito, invariante anti-confusión, disparadores hacia B); el resto son **operativas** y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — GraalVM CE como JDK del backend (Opción A — JIT)

Sustituir OpenJDK / Temurin por **GraalVM Community Edition** como JDK del proyecto.

Razón principal: el coste/beneficio de B (imagen nativa) no se justifica con los NFRs de un mono-club de 550 usuarios. Los argumentos a favor de B —cold start, memoria— solo cobran peso si se activa scale-to-zero en App Runner (ADR-0006 D4 lo descarta en MVP con `min=1`) o si la factura de memoria se vuelve un problema observable (D11). A cambio, B introduce *build time* x10, debugging complejo, tests duales y deuda técnica reversible solo con esfuerzo serio.

A aporta una mejora marginal de rendimiento (5-15 % en JIT con compilador Graal), es **reversible en un commit** (cambio de imagen base del `Dockerfile`) y prepara el terreno: si en el futuro D11 dispara la migración a B, ya estaremos sobre el mismo *vendor* de runtime.

Se descarta **C (OpenJDK puro)** solo a nivel narrativo. En la práctica, las diferencias operativas entre A y C son **mínimas**; A simplemente cambia el binario del JDK. Si en algún momento GraalVM CE planteara fricción operativa que no anticipamos, retroceder a C es una línea de `Dockerfile`.

<a id="d2"></a>
### D2 — Modo JIT explícitamente; no `native-image` en MVP

El modo de ejecución es **JIT** (Just-In-Time): el código se compila a bytecode, se ejecuta en la JVM de GraalVM y el compilador Graal optimiza en runtime. La aplicación sigue siendo un `jar` ejecutable empaquetado con `bootJar`.

**No se usa `native-image` ni Spring AOT en MVP** (ver D11 para disparadores de promoción a B). Esta decisión es explícita porque la confusión "tengo GraalVM, debería usar native-image" es una fuente común de deuda técnica accidental (D9).

Spring Modulith funciona en JIT **sin trabajo extra**. La validación específica de eventos / outbox / introspección de módulos solo aplica si se promueve a B.

<a id="d3"></a>
### D3 — Versión: runtime GraalVM CE 25 (Java 25 LTS), target de compilación Java 21

- **Runtime**: **GraalVM CE 25** sobre **Java 25 LTS** (ADR-0001 D12, política "LTS vigente"; Java 25 es LTS desde sep-2025).
- **Target de compilación: Java 21** — detekt 1.23.7 y Kotlin 2.1.0 **no soportan `jvm-target 25`** (verificado en CI el 2026-06-03; detekt tope 22). El jar (bytecode 21) corre en el runtime 25 (forward-compatible). Se alinea la compilación a 25 cuando esas herramientas lo soporten (disparador de D5). Upgrade aplicado por el cauce de D5, sin reabrir A = JIT.
- **Minor**: la última estable disponible al iniciar el desarrollo, **pin del tag mayor** en `Dockerfile` (no del minor, para recibir parches automáticamente con la imagen base).
- Cuando salga una nueva LTS de Java soportada por GraalVM CE, se evalúa el upgrade según ADR-0001 D12 + el checklist de D5.

<a id="d4"></a>
### D4 — Imagen base: `ghcr.io/graalvm/jdk-community:25` (runtime; build stage `:21`)

- **Imagen base del Dockerfile**: runtime `ghcr.io/graalvm/jdk-community:25`; **build stage `:21`** (compila a target Java 21, ver D3).
- Razones:
  - **Coherencia con GHCR** como registry del proyecto (ADR-0010 D3).
  - **Imagen oficial comunitaria** de GraalVM, mantenida por la comunidad / Oracle.
  - **Runtime soporta Java 25 LTS** alineado con la política de ADR-0001 D12.
- Tamaño esperado: ~200 MB (similar a Temurin, dentro del NFR de +50 MB).
- Si el tamaño de la imagen final se vuelve problemático, se evalúa una variante slim o `jlink` para empaquetar solo los módulos JDK necesarios (sin reabrir este ADR — es optimización de empaquetado).

<a id="d5"></a>
### D5 — Política de revisión: semestral + ante nueva minor con CVE

- **Revisión semestral** del pin de versión en `Dockerfile`: ¿hay nueva minor estable? ¿el upgrade no rompe nada en CI?
- **Revisión inmediata** ante anuncio de nueva minor con **CVE crítica** que afecte al JDK.
- **Revisión anual o por LTS** del mayor: al salir nueva LTS de Java soportada por GraalVM CE, se evalúa el upgrade siguiendo ADR-0001 D12.
- El procedimiento de actualización vive en `docs/runbooks/actualizacion-jdk.md`: bump del Dockerfile + correr checklist de D8 + PR.

<a id="d6"></a>
### D6 — Setup en CI con `actions/setup-java` (`graalvm-community`)

GitHub Actions configura GraalVM CE con la acción estándar:

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: 'graalvm-community'
    java-version: '21'
    cache: 'gradle'
```

- **Sin acción específica de GraalVM**: la distribución `graalvm-community` está integrada en `setup-java` desde 2024.
- Coherente con el resto del pipeline de ADR-0010.
- El runner descarga GraalVM CE 21 — el JDK de build/test, alineado con el target de compilación Java 21 (D3); el runtime de producción es CE 25 (D4) — desde el mirror oficial al primer uso; cacheado por la propia acción.

<a id="d7"></a>
### D7 — GraalVM CE también en local con toolchain Gradle

El desarrollador en local usa **el mismo GraalVM CE de build** que CI (CE 21, target de compilación de D3; el runtime de producción es CE 25 y ejecuta ese mismo bytecode, D4). Sin divergencia "funciona en mi máquina" en el build:

- En `build.gradle.kts`:

  ```kotlin
  java {
      toolchain {
          languageVersion.set(JavaLanguageVersion.of(21))
          vendor.set(JvmVendorSpec.GRAAL_VM)
      }
  }
  ```

- **Gradle Toolchains + Foojay Resolver** descarga GraalVM CE automáticamente al primer build local. Sin pasos manuales.
- El desarrollador puede usar SDKMAN o instalarlo a mano si prefiere, pero la toolchain garantiza que Gradle use el correcto independientemente.

<a id="d8"></a>
### D8 — Checklist de validación antes de cerrar H0

Antes de cerrar H0 (primera entrega del piloto), se verifica:

- [ ] **Build pasa** con GraalVM CE 21 (JDK de build/test, D6) en CI.
- [ ] **Tests unitarios + de integración** pasan en GraalVM CE.
- [ ] **Tamaño de imagen Docker** resultante: **< Temurin + 50 MB**.
- [ ] **Smoke test**: la app arranca en App Runner con perfil `staging` y responde 200 a `/actuator/health`.
- [ ] **Métricas de latencia** comparables a Temurin en pruebas de carga ligeras (p95 < 400 ms con 50 RPS sostenidos durante 5 min).
- [ ] **Plugins de Gradle** no muestran warnings de incompatibilidad con GraalVM CE.
- [ ] **JFR / profilers** funcionan en JIT igual que con Temurin (verificado con grabación de 30 s).

Si algún check falla y no se puede resolver en H0, **retroceder a Temurin** (Opción C) es la salida — cambio de una línea en Dockerfile y `vendor.set(JvmVendorSpec.ADOPTIUM)` en Gradle.

<a id="d9"></a>
### D9 — Invariante anti-confusión: GraalVM CE ≠ imagen nativa

**Usar GraalVM CE como JDK no implica usar `native-image`**. Son dos decisiones independientes:

- **A (este ADR)**: GraalVM CE como JDK en modo JIT. Sigue habiendo *jar*, JVM, debugging habitual.
- **B (futuro condicionado a D11)**: imagen nativa con Spring AOT + `native-image`. Cambia el modelo de despliegue.

**Cualquier intento de "saltar a B" requiere reabrir este ADR** con los disparadores de D11 cumplidos. Concretamente, prohibido:

- Activar Spring AOT preparation (`spring-boot-aot` plugin) sin reabrir el ADR.
- Añadir tests nativos en CI sin reabrir el ADR.
- Modificar el Dockerfile para usar `native-image` sin reabrir el ADR.

Esta invariante es **anti-deuda accidental**: el equipo que llega nuevo no debe asumir "tengo GraalVM, optimizo a nativo" sin medir.

<a id="d10"></a>
### D10 — GraalVM Enterprise descartado por licencia

**GraalVM Enterprise** (con compilador propietario, hasta hace poco bajo licencia comercial) **no entra en consideración**:

- GraalVM CE es suficiente para nuestros NFRs.
- Enterprise añade dependencia de un proveedor comercial sin justificación al volumen del piloto.
- Si Oracle cambia el modelo de licencia y CE perdiera viabilidad, **retroceder a C (Temurin)** es la salida limpia — no comprometerse con Enterprise.

<a id="d11"></a>
### D11 — Disparadores cuantitativos para promover A → B

La promoción **A → B (imagen nativa)** se activa **solo con datos de la beta**, no por anticipación. Disparadores concretos (cualquiera dispara la reapertura):

- **Disparador económico**: factura sostenida **> 200 €/mes durante 2 meses consecutivos** atribuible a memoria de App Runner (no a otro componente). Cruce con ADR-0006 D26 (alarma crítica 200 €/mes).
- **Disparador de scale-to-zero**: se decide activar scale-to-zero en App Runner (por horas valle muy marcadas) y el cold start observado de la JVM (3-5 s) penaliza UX medible (latencia p99 visible al usuario en > 5 % de peticiones tras valle).
- **Disparador de madurez**: Spring Native madura un paso más y librerías clave que hoy son cuestionables (drivers JDBC complejos, librerías de validación con reflection) muestran soporte AOT robusto documentado por sus mantenedores.

Sin alguno de esos disparadores, **esta decisión no se reabre**. La revisión periódica (Notas) verifica si alguno se ha activado.

## Consecuencias

### Positivas

- **Cambio de runtime indoloro**: el equipo cambia OpenJDK por GraalVM CE sin tocar código, sin tocar tests, sin tocar pipeline. Single point of change: el `Dockerfile` y la toolchain de Gradle.
- **Mejora marginal de rendimiento** (5-15 %) gratis en JIT cargas no críticas.
- **Mismo modelo mental** que el equipo viene esperando — *jars*, JVM, debugging y profiling habituales.
- **Reversibilidad total**: si GraalVM CE introduce fricción inesperada, retroceder a C (Temurin) es una línea de configuración.
- **Puerta abierta a B** sin coste de migración añadido cuando los disparadores de D11 activen la decisión.
- **Sin divergencia local-CI en el build** (D7): el mismo JDK de build en local y CI; el runtime de producción (CE 25, D4) ejecuta ese mismo bytecode target 21.
- **Anti-confusión D9** como invariante explícito: el equipo no se confunde sobre qué es A y qué es B.
- **Checklist de validación H0** (D8) hace falsable la decisión: si los checks fallan, retroceder.

### Negativas / coste asumido

- **Beneficio cuantificable modesto** sobre OpenJDK estándar. Para 550 usuarios y carga baja, la diferencia será difícil de medir.
- **Compromiso explícito con un JDK menos común** que Temurin/Adoptium. Soporte de la comunidad ligeramente menor (pero ya muy maduro a estas alturas).

### Riesgos y mitigaciones

- **Algún plugin de Gradle o herramienta de build tenga problema con GraalVM CE en lugar de Temurin** → checklist D8 lo detecta antes de H0. Si aparece un problema bloqueante, retroceder a C (Temurin) con cambio de una línea.
- **El equipo asume "estoy usando GraalVM, debería tener imagen nativa"** → invariante explícito en D9; cualquier intento de saltar a B requiere reabrir este ADR con los disparadores de D11 cumplidos.
- **Una nueva LTS de Java sale antes que la versión soportada por GraalVM CE** → política de revisión (D5) anota el riesgo; si GraalVM CE se queda atrás en una LTS clave, se valora retroceder a C temporalmente.
- **Oracle cambia el modelo de licencia de GraalVM** → ya cubierto en D10: la salida limpia es C, no Enterprise.
- **Toolchain Gradle no descarga GraalVM CE al primer build local** (Foojay sin entrada) → instalación manual documentada en el onboarding como fallback.

## Notas

- Las premisas heredadas son **invariantes de este ADR**: si cambian (especialmente ADR-0006 D4 sobre scale-to-zero, ADR-0006 D26 sobre coste objetivo, ADR-0007 sobre Spring Modulith), este ADR se revisita.
- **Versión actual**: runtime **GraalVM CE 25** (Java 25 LTS); **compilación en target Java 21** (detekt 1.23.7 / Kotlin 2.1.0 no soportan jvm-target 25, verificado en CI el 2026-06-03). Alinear la compilación a 25 cuando lo soporten. Política de revisión en D5.
- Este ADR **no fija** la configuración de App Runner (scale-to-zero, número de instancias mínimas, tamaño de instancia) — eso vive en ADR-0006. Aquí solo se acota qué runtime usamos.
- **Confirmación**: Antonio (decisor) confirma la Opción A el 2026-05-27. La promoción a B queda condicionada a los disparadores de D11, no a anticipación.
- **Revisión periódica**: este ADR se revisa cada **6 meses** (cruce a D5) o cuando alguno de los disparadores de D11 se active. La revisión también verifica si la versión pinned sigue siendo la recomendada por GraalVM CE.
- **Reorganización del 2026-05-30 (Nivel 1)**: el ADR se reestructura con índice de sub-decisiones (párrafo introductorio + tabla), premisas heredadas, NFRs propios complementarios (build time, tamaño imagen), numeración D1-D11 con anchors. Decisiones nuevas o explicitadas: versión pinned con política de revisión (D3, D5), imagen base concreta (D4), setup en CI con `actions/setup-java` (D6), GraalVM CE también en local con toolchain Gradle (D7), checklist de validación pre-H0 (D8), invariante anti-confusión como sub-decisión propia (D9), disparador económico cuantitativo cruzado con ADR-0006 D26 (D11).
