package com.runcriticon.auditoria.infrastructure.persistence.repositories

import com.runcriticon.auditoria.infrastructure.persistence.entities.AuditoriaEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Solo la escritura (`save`) pasa por Spring Data; la consulta forense y la anonimización van por JDBC plano en
 * [AuditEventRepositoryImpl] — filtros dinámicos y un `UPDATE` masivo no encajan bien en el derived-query de
 * Spring Data.
 *
 * **No se llama `AuditEventEntityRepository`** pese a ser el nombre natural: `identidad` ya tiene una interfaz
 * con ese simple name para su propia `evento_auditoria` (ADR-0003 D15), y Spring registra los repositorios de
 * datos por simple class name sin distinguir paquete — dos con el mismo nombre chocan al arrancar el contexto.
 * Mismo motivo por el que `StudentDirectoryController` no se llama `StudentController`.
 */
interface AuditoriaEventEntityRepository : JpaRepository<AuditoriaEventEntity, UUID>
