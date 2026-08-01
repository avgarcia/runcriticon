# RGPD en módulos — guía de referencia

Subdocumento de [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md). Cubre los **detalles de protección de datos por módulo** que la guía principal resume: categorización de tablas, consumo del evento `AlumnoEliminado`, patrón de borrado mixto en práctica, anonimización de datos derivados, captura técnica del consentimiento, auditoría de accesos a datos sensibles.

> Espejo aplicado de **ADR-0014** (categorización, borrado mixto, retención, RAT, consentimiento, brechas) y **ADR-0009 D15-D17** (auditoría de autorización en módulo `auditoria`). Si hay conflicto, gana el ADR.

## 1. Propósito y alcance

Cualquier módulo que **persiste datos personales** del alumno debe cumplir tres responsabilidades RGPD:

1. **Declarar la categoría** de cada tabla que persiste (PII primaria, auditoría, outbox, etc.).
2. **Consumir el evento `AlumnoEliminado`** y aplicar el patrón de borrado mixto correcto a cada tabla.
3. **Emitir el evento `AccesoADatosSensibles`** cuando un caso de uso lea o modifique datos sensibles, para que el módulo `auditoria` lo registre.

Módulos típicos del MVP con PII y su tratamiento:

| Módulo | Tablas con PII | Tratamiento al ejercer olvido |
|---|---|---|
| **Identidad** | `identidad.usuario`, `identidad.invitacion`, `identidad.magic_link`, `identidad.password_historico` | Borrado físico (cat. 1) — implementado en `DeleteUserCommand` |
| **Identidad** | `identidad.evento_auditoria` | Anonimización (cat. 2) — **pendiente**: hoy el borrado conserva `actor_id`, `sujeto_id` e `ip` |
| **Club y taxonomía** | `club_taxonomia.persona` (proyección), `club_taxonomia.alumno_tag` | Borrado físico (cat. 1) — implementado en `StudentDeletionListener` |
| **Planificación** | `planificacion.personalizacion`, `planificacion.miembros_grupo` (proyección) | Borrado físico (cat. 1 — son derivados pero referencian a alumno) |
| **Seguimiento** | `seguimiento.alumno_perfil`, `seguimiento.marca`, `seguimiento.reporte_sesion` | Borrado físico (cat. 1) |
| **Auditoría** | `auditoria.evento` | Anonimización (cat. 3) |

## 2. Categorización de tablas con `@RgpdCategory`

Cada entidad JPA del módulo declara su **categoría RGPD** explícitamente. Una anotación propia + un comentario obligatorio en la migración SQL.

### Anotación

```kotlin
// shared/rgpd/RgpdCategory.kt
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RgpdCategory(val category: Category)

enum class Category(val code: Int, val description: String) {
    PII_PRIMARIA(1, "Datos personales identificables del alumno"),
    AUDITORIA_IDENTIDAD(2, "Auditoría de eventos de identidad"),
    AUDITORIA_AUTORIZACION(3, "Auditoría de accesos y denegaciones"),
    OUTBOX(4, "Eventos del outbox de Spring Modulith"),
    BACKUPS(5, "Backups gestionados por la plataforma"),
    LOGS_OPERATIVOS(6, "Logs operativos en CloudWatch / Loki"),

    // Otras categorías técnicas (no PII, sin restricciones RGPD especiales)
    SIN_PII(0, "Datos del club, taxonomía, configuración"),
}
```

### Uso en entidades

```kotlin
// seguimiento/infrastructure/persistence/AlumnoPerfilEntity.kt
@Entity
@Table(name = "alumno_perfil", schema = "seguimiento")
@RgpdCategory(Category.PII_PRIMARIA)
class AlumnoPerfilEntity( /* ... */ )

// auditoria/infrastructure/persistence/EventoAuditoriaEntity.kt
@Entity
@Table(name = "evento", schema = "auditoria")
@RgpdCategory(Category.AUDITORIA_AUTORIZACION)
class EventoAuditoriaEntity( /* ... */ )

// clubtaxonomia/infrastructure/persistence/TaxonomiaEntity.kt
@Entity
@Table(name = "tag_key", schema = "club_taxonomia")
@RgpdCategory(Category.SIN_PII)
class TagKeyEntity( /* ... */ )
```

### ArchUnit guard

```kotlin
// test/architecture/RgpdArchTest.kt
@AnalyzeClasses(packages = ["com.runcriticon"])
class RgpdArchTest {

    @ArchTest
    val `toda entidad JPA declara su categoria RGPD` =
        classes()
            .that().areAnnotatedWith(Entity::class.java)
            .should().beAnnotatedWith(RgpdCategory::class.java)
}
```

Falla el build si una entidad nueva entra sin categoría.

### Comentario obligatorio en migración SQL

Cruce con [`persistencia.md`](persistencia.md) §12. Cada `CREATE TABLE` declara la misma categoría en comentario:

```sql
-- Categoría 1 (PII_PRIMARIA): perfil del alumno con datos de salud.
-- Retención: hasta baja + 30 días de gracia. Borrado físico al consumir AlumnoEliminado.
CREATE TABLE seguimiento.alumno_perfil ( ... );
```

### Reportes automáticos para el RAT

Una utilidad de tests escanea el classpath y genera la lista de tablas + categorías. El equipo la usa para sincronizar `docs/legal/rat.md` (ADR-0014 D19):

```kotlin
// test/utility/GeneraRATTablas.kt
@Test
fun `genera lista de tablas con categoria RGPD para el RAT`() {
    val entidades = ClassPath.from(Thread.currentThread().contextClassLoader)
        .getTopLevelClassesRecursive("com.runcriticon")
        .map { it.load() }
        .filter { it.isAnnotationPresent(Entity::class.java) }

    val resumen = entidades.map { e ->
        val category = e.getAnnotation(RgpdCategory::class.java)?.category
        val tabla = e.getAnnotation(Table::class.java)
        "${tabla.schema}.${tabla.name} (cat. ${category?.code})"
    }.sorted()

    Path.of("build/reports/rat-tablas.txt").writeText(resumen.joinToString("\n"))
}
```

## 3. Consumo de las bajas de personas

El módulo Identidad publica **dos** eventos de supresión, `AlumnoEliminado` y `EntrenadorEliminado`, simétricos a los de alta: la PII de un entrenador merece el mismo trato que la de un alumno. Cada módulo con datos personales de una persona **debe** consumirlos y aplicar borrado mixto.

Los dos eventos viajan **sin `name` ni `email`**, a diferencia del resto: el payload sobrevive en el outbox al dato que se acaba de borrar. El sujeto se identifica por `aggregateId`.

### Patrón obligatorio: `StudentDeletionListener` por módulo

Implementación de referencia: `clubtaxonomia/application/listeners/StudentDeletionListener.kt`.

```kotlin
@Component
class StudentDeletionListener(
    private val personErasure: PersonErasure,
    @Qualifier("clubTaxonomiaProcessedEventTracker")   // por el literal: importar la constante del adaptador
    private val processedEvents: ProcessedEventTracker, // haría que `application` dependiera de `infrastructure`
    private val mdcRestorer: MdcRestorerForEvents,
) {
    @ApplicationModuleListener fun on(event: AlumnoEliminado) = purge(event)
    @ApplicationModuleListener fun on(event: EntrenadorEliminado) = purge(event)

    private fun purge(event: IntegrationEvent) {
        mdcRestorer.restore(module = "club_taxonomia", traceparent = event.traceparent,
                            clubId = event.clubId, actorId = event.actorId)
        try {
            if (!processedEvents.markIfNew("StudentDeletionListener", event.eventId)) return
            personErasure.erase(PersonId.of(event.aggregateId))
        } finally {
            mdcRestorer.clear()
        }
    }
}
```

El nombre del listener se mantiene aunque atienda también al entrenador: es el que busca el patrón (y el guard que lo verifique).

### ⚠️ Lápida: sin ella el borrado se deshace solo

Un módulo que mantenga una **proyección** alimentada por eventos no puede limitarse a borrar la fila. Su upsert protege el orden con `WHERE ... last_processed_event_ts >= ...`, pero esa condición cuelga de `ON CONFLICT DO UPDATE`: **solo actúa si la fila existe**. Si el borrado se procesa primero y luego llega un evento de alta rezagado de la misma persona, la sentencia toma la rama `INSERT` y **reinserta la PII**. La tabla `evento_procesado` no lo corta: son `event_id` distintos y los dos son nuevos.

El escenario real es un alta que agota sus reintentos, cae a la DLQ y se republica semanas después. La PII vuelve **para siempre**, porque no llegará ningún otro evento de supresión.

Por eso el borrado escribe una **lápida** (`{modulo}.persona_eliminada`, solo el id, categoría `SIN_PII`) que el upsert consulta antes de escribir. Es incondicional: descansa en que los identificadores de usuario son UUID v7 y nunca se reutilizan.

Además, ambas rutas toman un `pg_advisory_xact_lock` sobre el id de la persona. Sin él queda una carrera entre comprobar la lápida y escribir: la escritura mira, el borrado commitea, la escritura inserta igualmente. **La guarda depende del aislamiento `READ COMMITTED`**; elevarlo la rompería en silencio.

### ArchUnit guard

```kotlin
// test/architecture/StudentDeletionArchTest.kt
@ArchTest
val `modulo con tabla PII_PRIMARIA tiene StudentDeletionListener` = ArchRuleDefinition.rule {
    val modulosConPII = entidadesConCategoria(Category.PII_PRIMARIA)
        .map { it.modulo }
        .toSet()

    val modulosConListener = clasesEn("..application.listeners..")
        .filter { it.simpleName == "StudentDeletionListener" }
        .map { it.modulo }
        .toSet()

    modulosConPII - modulosConListener shouldBe emptySet()
}
```

El build falla si un módulo declara tabla con categoría 1 y no tiene `StudentDeletionListener`.

### Garantías

- **Idempotente** por construcción (tabla `evento_procesado`, ver [`persistencia.md`](persistencia.md) §7).
- **At-least-once** vía outbox de Spring Modulith (ADR-0007 D6).
- **Retentos 5 veces** ante fallo; DLQ + alarma si agota (ADR-0007 D13).
- **Plazo p95 < 24 h** para la propagación a todas las proyecciones (ADR-0014 NFR).

## 4. Borrado mixto en práctica

Cada categoría tiene su mecanismo. El `StudentDeletionListener` aplica el correcto a cada tabla del módulo.

### Categoría 1 — PII primaria → borrado físico

```kotlin
// clubtaxonomia/infrastructure/persistence/projections/PersonErasureJdbc.kt
@NoAuthScope(
    justificacion =
        "Borrado RGPD dirigido por integration events: sin principal en el listener; el sujeto lo identifica el " +
            "evento publicado por identidad, no entrada de usuario.",
)
override fun erase(personId: PersonId): ErasedRows {
    lockPerson(jdbc, personId.value)          // serializa con la escritura de la proyección
    jdbc.update(TOMBSTONE_SQL, personId.value) // lápida ANTES de borrar
    …
}
```

`@NoAuthScope` con justificación porque el borrado RGPD corre sin sesión humana: viene del listener, después del commit, sin `SecurityContext`. Un `@AuthScope(Scope.CLUB)` haría fallar cerrado al aspecto en cada entrega y las supresiones acabarían en la DLQ.

El `DELETE` va por el id de la persona **sin** filtrar por club: la clave primaria ya lo identifica, y un `club_id` que no cuadrara convertiría el borrado en un no-borrado silencioso. En una ruta de supresión, eso es peor que fallar ruidosamente.

### Categorías 2 y 3 — Auditoría → anonimización

Las tablas de auditoría **no se borran físicamente**: se conservan por responsabilidad proactiva (ADR-0014 D6). Se anonimizan los campos identificadores.

Función SQL centralizada:

```sql
-- _shared/V202605270000__crea_funcion_anonimiza_auditoria.sql
CREATE OR REPLACE FUNCTION anonimiza_evento_auditoria(p_alumno_id UUID)
RETURNS INTEGER AS $$
DECLARE
    filas_afectadas INTEGER := 0;
BEGIN
    -- Categoría 2: auditoría de identidad
    UPDATE identidad.evento_auditoria
       SET actor_id   = NULL,
           sujeto_id  = NULL,
           ip         = CASE
                          WHEN family(ip) = 4 THEN set_masklen(ip::cidr, 24)::inet
                          WHEN family(ip) = 6 THEN set_masklen(ip::cidr, 48)::inet
                          ELSE NULL
                        END,
           metadata   = metadata - 'email_hash' - 'email'
     WHERE actor_id  = p_alumno_id
        OR sujeto_id = p_alumno_id;
    GET DIAGNOSTICS filas_afectadas = ROW_COUNT;

    -- Categoría 3: auditoría de autorización
    UPDATE auditoria.evento
       SET actor_id   = NULL,
           sujeto_id  = NULL,
           ip         = CASE
                          WHEN family(ip) = 4 THEN set_masklen(ip::cidr, 24)::inet
                          WHEN family(ip) = 6 THEN set_masklen(ip::cidr, 48)::inet
                          ELSE NULL
                        END
     WHERE actor_id  = p_alumno_id
        OR sujeto_id = p_alumno_id;

    RETURN filas_afectadas;
END;
$$ LANGUAGE plpgsql;
```

Llamada desde el listener del módulo `auditoria`:

```kotlin
// auditoria/application/listeners/StudentDeletionListener.kt
@Component
class StudentDeletionListener(
    private val jdbc: JdbcTemplate,
    private val tracker: EventoProcesadoTracker,
) {
    @ApplicationModuleListener
    fun on(evento: AlumnoEliminado) {
        if (!tracker.marcarSiNuevo("auditoria.StudentDeletionListener", evento.eventId)) return

        // Llama a la función SQL centralizada
        jdbc.queryForObject(
            "SELECT anonimiza_evento_auditoria(?)",
            Int::class.java,
            evento.alumnoId.value,
        )
    }
}
```

### Categoría 4 — Outbox → caducidad pasiva

El outbox se compacta a 30 días (ADR-0007 D15). En el ínterin, los eventos pendientes se procesan; los ya procesados quedan disponibles para reproyección. **No se anonimizan a mano**: la compactación los elimina pasivamente.

Si un evento aún por entregar contiene PII del usuario borrado y la entrega tarda más de los 30 días, se acepta el caso borde: en la práctica los eventos se entregan en segundos/minutos.

### Categoría 5 — Backups → caducidad pasiva

Backups de RDS con retención 30 días (ADR-0006 D9). Datos borrados desaparecen al caducar el backup. **No se restauran selectivamente** para resucitar PII (ADR-0014 D8). El runbook de DR (ADR-0006 D29) reaplica la lista de olvidos pendientes tras una restauración completa.

### Categoría 6 — Logs operativos → no contienen PII

Los logs operativos en CloudWatch ya tienen la IP truncada (ADR-0011 D9, ADR-0014 D9) y el `userId` hasheado. **No es necesario borrarlos al ejercer olvido**: el hash con salt anual ya impide la reidentificación.

## 5. Auditoría de accesos con `@AuditaAcceso`

ADR-0009 D15-D17: cada acceso a datos sensibles (salud, perfil personal de terceros) emite el evento `AccesoADatosSensibles` que el módulo `auditoria` consume.

### Anotación

```kotlin
// shared/auditoria/AuditaAcceso.kt
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuditaAcceso(
    val tipo: TipoAcceso,
    val recurso: String,
)

enum class TipoAcceso {
    SALUD,             // marcas, sesiones, reportes, lesiones
    PERFIL_TERCERO,    // email, teléfono o dirección de OTRO usuario
}
```

### Uso

```kotlin
// seguimiento/application/VerPerfilAlumnoService.kt
@ApplicationService
class VerPerfilAlumnoService(
    private val perfilRepo: AlumnoPerfilRepository,
    private val autorizacionService: SeguimientoAutorizacionService,
    private val principalProvider: PrincipalProvider,
) {

    @AuditaAcceso(tipo = TipoAcceso.SALUD, recurso = "alumno_perfil")
    fun ejecutar(alumnoId: AlumnoId): Either<SeguimientoError, AlumnoPerfil> = either {
        val principal = principalProvider.actual()
        autorizacionService.puedeVerPerfilAlumno(principal, alumnoId).bind()

        perfilRepo.buscar(alumnoId)
            ?: raise(SeguimientoError.NotFound("AlumnoPerfil", alumnoId.value.toString()))
    }
}
```

### Aspecto que publica el evento

```kotlin
// shared/auditoria/AuditaAccesoAspect.kt
@Aspect
@Component
class AuditaAccesoAspect(
    private val publicador: PublicadorDeEventos,
    private val principalProvider: PrincipalProvider,
) {
    @AfterReturning(
        pointcut = "@annotation(auditaAcceso)",
        returning = "resultado",
    )
    fun publicarSiExito(joinPoint: JoinPoint, auditaAcceso: AuditaAcceso, resultado: Any?) {
        // Solo audita si el resultado es Either.Right (acceso efectivamente exitoso)
        if (resultado !is Either<*, *> || resultado.isLeft()) return

        publicador.publicar(AccesoADatosSensibles(
            eventId       = UUID.randomUUID(),
            aggregateId   = principalProvider.actual().userId,
            occurredAt    = Instant.now(),
            version       = 1,
            clubId        = principalProvider.actual().clubId,
            actorId       = principalProvider.actual().userId,
            traceparent   = OpenTelemetry.actualTraceparent(),
            tipoAcceso    = auditaAcceso.tipo,
            recurso       = auditaAcceso.recurso,
            argumentosHash = hashSeguro(joinPoint.args),
        ))
    }
}
```

### Política de auditoría de denegaciones

Las **denegaciones** (`AccesoDenegado`) las emite el `AutorizacionService` de cada módulo cuando devuelve `Result.Forbidden` o `ProjectionStale` (ADR-0009 D15). No requiere `@AuditaAcceso`: el servicio publica el evento directamente al detectar la denegación.

### Lo que NO se audita

- Lectura del propio perfil del usuario (acceso a sus propios datos).
- Listados públicos del club (taxonomía, plantillas, configuración).
- Operaciones administrativas del propio sistema (jobs internos, healthchecks).

## 6. Captura técnica del consentimiento

ADR-0014 D18 fija el patrón. El módulo Identidad tiene la tabla:

```sql
-- identidad/V202605280100__crea_consentimiento.sql
-- Categoría 1 (PII_PRIMARIA): consentimiento del usuario.
-- Retención: igual que identidad.usuario (hasta baja + 30 días).
-- Borrado físico al consumir AlumnoEliminado.
CREATE TABLE identidad.consentimiento (
    id              UUID PRIMARY KEY,
    usuario_id      UUID NOT NULL,                              -- sin FK (intra-esquema sí, pero diseñamos por capa)
    version_texto   VARCHAR(20) NOT NULL,                       -- ej. "v2026-05-30"
    concedido_en    TIMESTAMPTZ NOT NULL,
    revocado_en     TIMESTAMPTZ NULL,
    ip              INET NOT NULL,                              -- IP completa por seguridad (ADR-0014 D18)
    user_agent      TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, version_texto)
);

CREATE INDEX idx_consentimiento_usuario ON identidad.consentimiento(usuario_id);
```

Versiones del texto en repo: `docs/legal/consentimiento/v2026-05-30.md` con metadatos en frontmatter (fecha, idioma, cambios sustanciales sí/no).

### Flujo

```kotlin
// identidad/application/ConcederConsentimientoService.kt
@ApplicationService
class ConcederConsentimientoService(
    private val consentimientoRepo: ConsentimientoRepository,
    private val publicador: PublicadorDeEventos,
) {
    fun ejecutar(
        usuarioId: UsuarioId,
        versionTexto: String,
        ip: InetAddress,
        userAgent: String,
    ): Either<IdentidadError, Unit> = either {
        ensure(versionTexto == TextoConsentimiento.vigente()) {
            IdentidadError.VersionConsentimientoObsoleta
        }

        val consentimiento = Consentimiento.nuevo(
            usuarioId = usuarioId,
            versionTexto = versionTexto,
            concedidoEn = Instant.now(),
            ip = ip,
            userAgent = userAgent,
        )
        consentimientoRepo.guardar(consentimiento)

        publicador.publicar(ConsentimientoConcedido.from(consentimiento))
    }
}
```

### Revocación

Al revocar, el módulo Seguimiento (y cualquier módulo que dependa del consentimiento) consume `ConsentimientoRevocado` y rechaza nuevas operaciones de tratamiento. ADR-0014 D18.

## 7. Política de retención por módulo

Cada tabla del módulo declara su retención en la migración + el cron correspondiente. Cruce a [`persistencia.md`](persistencia.md) §12 (tabla completa). Resumen aplicado al módulo:

```sql
-- {modulo}/V20260601_NN__cron_purga_auditoria.sql
-- Job programado: una vez al mes purga filas de auditoria > 24 meses
-- Spring @Scheduled o pg_cron según ADR-0006

-- En código Kotlin:
@Component
class PurgaAuditoriaJob(private val jdbc: JdbcTemplate) {
    @Scheduled(cron = "0 0 3 1 * *")  // día 1 de cada mes a las 03:00
    fun purgar() {
        jdbc.update(
            "DELETE FROM auditoria.evento WHERE ts < now() - INTERVAL '24 months'"
        )
    }
}
```

Cada módulo con tablas de categoría 2 o 3 tiene su job de purga.

## 8. Tests RGPD obligatorios

### Test: `AlumnoEliminado` propaga borrado mixto

```kotlin
@Transactional
class StudentDeletionListenerSeguimientoTest : IntegrationTestBase() {

    @Autowired lateinit var listener: StudentDeletionListener
    @Autowired lateinit var perfilRepo: AlumnoPerfilRepository
    @Autowired lateinit var marcaRepo: MarcaRepository

    @Test
    fun `borra fisicamente PII primaria del alumno`() {
        // GIVEN
        val alumnoId = AlumnoId(UUID.randomUUID())
        perfilRepo.guardar(AlumnoPerfilBuilder().delAlumno(alumnoId).build())
        marcaRepo.guardar(MarcaBuilder().delAlumno(alumnoId).distancia10K().build())

        // WHEN
        listener.on(AlumnoEliminado(alumnoId = alumnoId, /* ... */))

        // THEN
        perfilRepo.buscar(alumnoId) shouldBe null
        marcaRepo.deAlumno(alumnoId).shouldBeEmpty()
    }

    @Test
    fun `es idempotente: consumir dos veces deja el mismo estado`() {
        val alumnoId = AlumnoId(UUID.randomUUID())
        perfilRepo.guardar(AlumnoPerfilBuilder().delAlumno(alumnoId).build())
        val evento = AlumnoEliminado(alumnoId = alumnoId, eventId = UUID.randomUUID(), /* ... */)

        listener.on(evento)
        listener.on(evento)  // misma idempotencia via evento_procesado

        perfilRepo.buscar(alumnoId) shouldBe null
    }
}
```

### Test: anonimización en auditoría

```kotlin
@Transactional
class StudentDeletionListenerAuditoriaTest : IntegrationTestBase() {

    @Test
    fun `anonimiza filas de auditoria con actor_id o sujeto_id del alumno`() {
        val alumnoId = AlumnoId(UUID.randomUUID())
        val eventoAuditoria = EventoAuditoriaEntity(
            id        = UUID.randomUUID(),
            tipo      = "LOGIN_OK",
            actorId   = alumnoId.value,
            sujetoId  = alumnoId.value,
            ts        = Instant.now(),
            ip        = "192.168.1.42".asInet(),
            metadata  = mapOf("email_hash" to "abc123"),
        )
        eventoAuditoriaRepo.save(eventoAuditoria)

        // WHEN
        listener.on(AlumnoEliminado(alumnoId = alumnoId, /* ... */))

        // THEN
        val fila = eventoAuditoriaRepo.findById(eventoAuditoria.id)!!
        fila.actorId shouldBe null
        fila.sujetoId shouldBe null
        fila.ip.toString() shouldBe "192.168.1.0"     // truncada /24
        fila.metadata shouldNotContainKey "email_hash"
    }
}
```

### Test: `@AuditaAcceso` publica evento

```kotlin
class VerPerfilAlumnoServiceAuditoriaTest : IntegrationTestBase() {

    @Autowired lateinit var service: VerPerfilAlumnoService
    @Autowired lateinit var outboxRepo: EventPublicationRepository

    @Test
    fun `ver perfil de salud publica AccesoADatosSensibles`() {
        val (entrenador, alumno) = TestEscenarios.entrenadorYAlumnoDelMismoGrupo()
        TestPrincipalContext.set(entrenador)

        service.ejecutar(alumno.id).shouldBeRight()

        val evento = outboxRepo.findByEventType("AccesoADatosSensibles").lastOrNull()
        evento shouldNotBe null
    }
}
```

### Test: ArchUnit guards RGPD

Ya cubiertos en [`testing-de-modulos.md`](testing-de-modulos.md) §6. Resumen específico RGPD:

- Toda `@Entity` declara `@RgpdCategory`.
- Cada módulo con tabla `PII_PRIMARIA` tiene `StudentDeletionListener`.
- Métodos `@AuditaAcceso` solo en `@ApplicationService`.

## 9. Catálogo de eventos RGPD del módulo

Cada módulo declara, en su `README.md` de RGPD (`backend/src/main/kotlin/com/runcriticon/{modulo}/RGPD.md`), su catálogo:

```markdown
# RGPD — módulo Seguimiento

## Tablas con datos personales

| Tabla | Categoría | Retención | Borrado al olvido |
|---|---|---|---|
| seguimiento.alumno_perfil | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| seguimiento.marca | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| seguimiento.reporte_sesion | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |

## Eventos consumidos

| Evento | Origen | Acción |
|---|---|---|
| `AlumnoEliminado` | Identidad | StudentDeletionListener: DELETE en las 3 tablas |
| `ConsentimientoRevocado` | Identidad | Marca alumno como "tratamiento bloqueado" |

## Eventos publicados

| Evento | Cuándo | Consumido por |
|---|---|---|
| `AccesoADatosSensibles` | Cada lectura/modificación con `@AuditaAcceso` | Módulo `auditoria` |
| `MarcaActualizada` | Cuando un alumno actualiza una de sus marcas privadas | Internamente para auditoría |

## Pendientes jurídicos del módulo

- Confirmar con asesoría legal el tratamiento de **datos médicos derivados** (lesiones reportadas) — ¿cumple la cat. 1 con borrado físico?
```

## 10. Checklist RGPD al crear un módulo

- [ ] Cada `@Entity` declara `@RgpdCategory(Category.X)` con la categoría correcta `(ADR-0014 D5)`
- [ ] Cada `CREATE TABLE` lleva comentario con la categoría y la retención `(ADR-0014 D5)`
- [ ] Si el módulo tiene tabla `PII_PRIMARIA`: implementado `StudentDeletionListener` con borrado físico de cada tabla `(ADR-0014 D7)`
- [ ] Si el módulo tiene tabla de categoría 2 o 3: implementado `StudentDeletionListener` con llamada a `anonimiza_evento_auditoria(p_alumno_id)` `(ADR-0014 D6)`
- [ ] `StudentDeletionListener` es idempotente vía tabla `evento_procesado` `(ADR-0007 D9)`
- [ ] Métodos de `@ApplicationService` que leen o modifican datos sensibles llevan `@AuditaAcceso(TipoAcceso.X, recurso = "...")` `(ADR-0009 D15)`
- [ ] El aspecto `AuditaAccesoAspect` está registrado en la configuración del módulo
- [ ] Jobs de purga programados para tablas con categoría 2 o 3 `(ADR-0014 D10)`
- [ ] ArchUnit guards activos: `@Entity` → `@RgpdCategory`, módulo con PII → `StudentDeletionListener`, `@AuditaAcceso` solo en `@ApplicationService` `(ADR-0008 D14)`
- [ ] Tests de integración del módulo verifican: borrado físico al consumir `AlumnoEliminado`, anonimización correcta donde aplica, idempotencia del listener, emisión de `AccesoADatosSensibles` con `@AuditaAcceso`
- [ ] `RGPD.md` del módulo creado con tablas, eventos consumidos, eventos publicados, pendientes jurídicos
- [ ] Si el módulo introduce un tratamiento nuevo: actualizar `docs/legal/rat.md` en la misma PR `(ADR-0014 D19)`

## Referencias

- **ADR-0009 D15, D16, D17** — auditoría de autorización: alcance, emisión vía evento, módulo `auditoria` dedicado.
- **ADR-0014 D5** — categorización de datos en 6 grupos.
- **ADR-0014 D6** — borrado mixto: físico para PII primaria, anonimización para auditoría.
- **ADR-0014 D7** — propagación del borrado vía evento `AlumnoEliminado` con política de fallos del outbox.
- **ADR-0014 D8** — backups con retención acotada, no se restauran selectivamente.
- **ADR-0014 D9** — anonimización de IPs en logs operativos.
- **ADR-0014 D10** — política de retención por categoría con disparador de purga.
- **ADR-0014 D11-D15** — derechos del interesado: acceso, rectificación, supresión, oposición, limitación.
- **ADR-0014 D16, D18** — base legal de salud (consentimiento Art. 9.2.a) y captura técnica.
- **ADR-0014 D17** — menores excluidos del MVP con disparador.
- **ADR-0014 D19** — RAT en `docs/legal/rat.md` con pre-commit hook.
- **ADR-0014 D22** — subencargados con DPA: AWS, Postmark, GitHub.
- **ADR-0011 D9** — anonimización de IPs y `userId` hasheado en logs operativos.
- [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md) — guía principal.
- [`persistencia.md`](persistencia.md) §12 — política de retención por categoría con tabla detallada.
- [`testing-de-modulos.md`](testing-de-modulos.md) §6 — ArchUnit guards.
