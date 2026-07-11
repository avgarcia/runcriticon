package com.runcriticon.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.Query

/**
 * Guard de fronteras entre schemas a nivel JPA/SQL (ADR-0004 D4): ninguna entidad enlaza vía
 * `@JoinColumn` con una entidad de otro schema, y ninguna `@Query(nativeQuery = true)` referencia el
 * schema de otro módulo. Ambas reglas pasan vacías (`allowEmptyShould`) hasta que exista el primer
 * caso real — hoy solo `identidad` está implementado y no tiene relaciones cross-schema ni
 * `nativeQuery`; empiezan a morder en cuanto aparezca el primer caso (Bloque 3+).
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class SchemaFronterasArchTest {
    @ArchTest
    val `ningun JoinColumn apunta a una entidad de otro schema` =
        classes()
            .that()
            .areAnnotatedWith(Entity::class.java)
            .should(notJoinColumnCrossSchema())
            .allowEmptyShould(true)

    @ArchTest
    val `ninguna Query nativeQuery referencia el schema de otro modulo` =
        classes()
            .that()
            .areInterfaces()
            .should(notNativeQueryCrossSchema())
            .allowEmptyShould(true)

    private fun notJoinColumnCrossSchema() =
        object : ArchCondition<JavaClass>("no tener @JoinColumn hacia una entidad de otro schema") {
            override fun check(
                entity: JavaClass,
                events: ConditionEvents,
            ) {
                val ownSchema = entity.tableSchemaOrNull() ?: return
                entity.fields
                    .filter { it.isAnnotatedWith(JoinColumn::class.java) }
                    .forEach { field ->
                        val targetSchema = field.rawType.tableSchemaOrNull()
                        if (targetSchema != null && targetSchema != ownSchema) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    entity,
                                    "${entity.name}.${field.name} tiene @JoinColumn hacia ${field.rawType.name} " +
                                        "(schema '$targetSchema'), pero ${entity.simpleName} vive en schema " +
                                        "'$ownSchema' — sin FK cruzando schemas (ADR-0004 D4).",
                                ),
                            )
                        }
                    }
            }
        }

    private fun notNativeQueryCrossSchema() =
        object : ArchCondition<JavaClass>("no tener @Query nativeQuery hacia el schema de otro módulo") {
            override fun check(
                repository: JavaClass,
                events: ConditionEvents,
            ) {
                val ownModule = repository.packageName.moduleSchemaOrNull() ?: return
                val otherSchemas = MODULE_SCHEMAS - ownModule
                repository.methods
                    .mapNotNull { method -> method.nativeQueryValueOrNull()?.let { method to it } }
                    .forEach { (method, sql) ->
                        val referenced =
                            otherSchemas.firstOrNull { schema -> Regex("\\b$schema\\.").containsMatchIn(sql) }
                        if (referenced != null) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    repository,
                                    "${repository.name}.${method.name} tiene @Query(nativeQuery=true) que " +
                                        "referencia el schema '$referenced', fuera de su módulo '$ownModule' " +
                                        "(ADR-0004 D4).",
                                ),
                            )
                        }
                    }
            }
        }

    private fun JavaClass.tableSchemaOrNull(): String? =
        if (isAnnotatedWith(Table::class.java)) {
            getAnnotationOfType(Table::class.java).schema.ifBlank { null }
        } else {
            null
        }

    private fun JavaMethod.nativeQueryValueOrNull(): String? =
        if (isAnnotatedWith(Query::class.java)) {
            val query = getAnnotationOfType(Query::class.java)
            if (query.nativeQuery) query.value else null
        } else {
            null
        }

    private fun String.moduleSchemaOrNull(): String? =
        MODULE_SCHEMAS.firstOrNull { schema ->
            this == "com.runcriticon.$schema" || startsWith("com.runcriticon.$schema.")
        }

    private companion object {
        val MODULE_SCHEMAS = setOf("identidad", "club_taxonomia", "planificacion", "seguimiento", "auditoria")
    }
}
