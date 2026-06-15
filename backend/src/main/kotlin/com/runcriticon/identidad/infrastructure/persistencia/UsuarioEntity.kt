package com.runcriticon.identidad.infrastructure.persistencia

import com.runcriticon.shared.rgpd.Categoria
import com.runcriticon.shared.rgpd.CategoriaRGPD
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Mapeo JPA del usuario (ADR-0003 D2). Contiene PII (email, nombre): categoría RGPD primaria
 * (ADR-0014). El plugin kotlin-jpa genera el constructor sin argumentos que exige Hibernate.
 */
@Entity
@Table(name = "usuario", schema = "identidad")
@CategoriaRGPD(Categoria.PII_PRIMARIA)
class UsuarioEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "club_id", nullable = false)
    var clubId: UUID,
    @Column(name = "email", nullable = false)
    var email: String,
    @Column(name = "email_normalizado", nullable = false)
    var emailNormalizado: String,
    @Column(name = "nombre", nullable = false)
    var nombre: String,
    @Column(name = "rol", nullable = false)
    var rol: String,
    @Column(name = "password_hash")
    var passwordHash: String?,
    @Column(name = "estado", nullable = false)
    var estado: String,
    @Column(name = "creado_en", nullable = false)
    var creadoEn: Instant,
    @Column(name = "modificado_en", nullable = false)
    var modificadoEn: Instant,
)
