package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.runcriticon.clubtaxonomia.domain.tag.Distance
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory

/**
 * Serializa/deserializa [TagValueMetadata] a la columna `metadata` (JSONB) de `tag_value`.
 *
 * El dominio no lleva anotaciones de Jackson: cero imports de framework en `domain`. El tipado polimórfico ("tipo":
 * "Empty"|"Race") lo aporta un mixin declarado aquí, en `infrastructure`. El `ObjectMapper` es propio del converter
 * (no el bean compartido de Spring): un `@Converter` JPA lo instancia el proveedor de persistencia con su
 * constructor sin argumentos, no el contenedor de Spring.
 *
 * [Distance] se persiste por su [Distance.code] (`"5K"`, no `"K5"`), coherente con el resto de la metadata y con lo
 * que viaja en eventos.
 *
 * Degradación ante corrupción: un JSON que no deserializa (edición manual, migración de otro sistema, bug de otra
 * versión de la app) no debe tumbar la lectura de la fila — se degrada a [TagValueMetadata.Empty] y se registra un
 * error. El `trace_id` no se añade a mano: `logback-spring.xml` ya vuelca el MDC (incluido `trace_id`) en cada línea
 * de log.
 */
@Converter(autoApply = false)
class TagValueMetadataJsonbConverter : AttributeConverter<TagValueMetadata, String> {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val mapper: ObjectMapper =
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .addMixIn(TagValueMetadata::class.java, TagValueMetadataMixin::class.java)
            .registerModule(
                SimpleModule()
                    .addSerializer(Distance::class.java, DistanceSerializer())
                    .addDeserializer(Distance::class.java, DistanceDeserializer()),
            )

    override fun convertToDatabaseColumn(attribute: TagValueMetadata?): String =
        mapper.writeValueAsString(attribute ?: TagValueMetadata.Empty)

    override fun convertToEntityAttribute(dbData: String?): TagValueMetadata {
        if (dbData.isNullOrBlank()) return TagValueMetadata.Empty
        return try {
            mapper.readValue(dbData, TagValueMetadata::class.java)
        } catch (e: JsonProcessingException) {
            logger.error("metadata de tag_value ilegible, degradando a Empty: {}", dbData, e)
            TagValueMetadata.Empty
        }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "tipo")
@JsonSubTypes(
    JsonSubTypes.Type(value = TagValueMetadata.Empty::class, name = "Empty"),
    JsonSubTypes.Type(value = TagValueMetadata.Race::class, name = "Race"),
)
private abstract class TagValueMetadataMixin

private class DistanceSerializer : StdSerializer<Distance>(Distance::class.java) {
    override fun serialize(
        value: Distance,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) = gen.writeString(value.code)
}

private class DistanceDeserializer : StdDeserializer<Distance>(Distance::class.java) {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): Distance {
        val code = p.valueAsString
        return Distance.fromCode(code)
            ?: throw ctxt.weirdStringException(code, Distance::class.java, "distancia desconocida")
    }
}
