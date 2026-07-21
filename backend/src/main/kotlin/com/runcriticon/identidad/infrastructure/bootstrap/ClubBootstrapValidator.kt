package com.runcriticon.identidad.infrastructure.bootstrap

import com.runcriticon.identidad.infrastructure.persistence.repositories.ClubEntityRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Verifica al arrancar que existe una fila de `identidad.club` para `runcriticon.bootstrap.club-id`.
 *
 * Corre en **todos los entornos** (sin `@Profile`, a diferencia de [IdentidadSeeder]): la FK
 * `usuario.club_id → club.id` (migración `V202607210001`) solo falla al dar de alta el primer usuario,
 * no al arrancar ni al hacer login (ambos solo leen). Si un entorno sobreescribe
 * `runcriticon.bootstrap.club-id` a un UUID que la migración no sembró, esa violación de FK sería opaca
 * y tardía; esta guarda la convierte en un fallo de arranque legible.
 */
@Component
class ClubBootstrapValidator(
    private val clubRepository: ClubEntityRepository,
    @Value("\${runcriticon.bootstrap.club-id:00000000-0000-0000-0000-000000000001}")
    private val clubId: String,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val id = UUID.fromString(clubId)
        check(clubRepository.existsById(id)) {
            "runcriticon.bootstrap.club-id=$clubId no tiene fila en identidad.club. " +
                "La fila la crea la migración V202607210001 (semilla defensiva sobre los club_id " +
                "existentes en identidad.usuario más el id canónico de bootstrap); si este entorno usa " +
                "un club-id distinto, siembra la fila antes de arrancar."
        }
    }
}
